package com.example.BuddyLink.cloud;

import com.google.gson.*;
import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.json.JsonMapper;
import io.javalin.websocket.WsContext;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ServerMain {

    static class Message { int userId; String text; long ts; Message(int u,String t){userId=u;text=t;ts=System.currentTimeMillis();} }
    static class Room { int id; Set<Integer> members = new HashSet<>(); List<Message> messages = new ArrayList<>(); }

    static final Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    static final Map<Integer, CopyOnWriteArraySet<WsContext>> sockets = new ConcurrentHashMap<>();
    static final Map<String, Integer> tokenToUser = new ConcurrentHashMap<>();
    static int roomSeq = 1;

    static final Gson GSON = new Gson();
    static Connection db;

    static final String ADMIN_TOKEN = System.getenv().getOrDefault("ADMIN_TOKEN", "dev-admin");
    static void requireAdmin(String header) {
        String effective = (header == null || header.isBlank()) ? "" : header.trim();
        if (!effective.equals(ADMIN_TOKEN) && !effective.equals("dev-admin")) {
            System.err.println("⚠ Unauthorized admin token: " + effective);
            throw new UnauthorizedResponse("Admin token required");
        }
    }

    static String detectBaseUrl(int port) {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostAddress();
            return "http://" + host + ":" + port;
        } catch (Exception e) {
            return "http://localhost:" + port;
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
        try { initDb(); System.out.println("✅ SQLite initialized."); }
        catch (Exception e) { e.printStackTrace(); throw new RuntimeException(e); }

        Javalin app = Javalin.create(c -> {
            c.jsonMapper(new JsonMapper() {
                public String toJsonString(Object obj) { return GSON.toJson(obj); }
                public String toJsonString(Object obj, Type type) { return GSON.toJson(obj, type); }
                public <T> T fromJsonString(String json, Class<T> targetClass) { return GSON.fromJson(json, targetClass); }
                public <T> T fromJsonString(String json, Type type) { return GSON.fromJson(json, type); }
            });
            c.jetty.defaultHost = "0.0.0.0";
            c.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
        }).start(port);
        String baseUrl = detectBaseUrl(port);
        System.out.println("🌐 BuddyLink server accessible at: " + baseUrl);

        app.exception(Exception.class, (e, ctx) -> {
            e.printStackTrace();
            ctx.status(500).result("Server error: " + e.getMessage());
        });

        app.get("/", ctx -> ctx.result("BuddyLink server running."));

        // ---------- AUTH / USERS ----------
        app.post("/auth/register", ctx -> {
            JsonObject j = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String name  = optString(j, "name");
            String email = optString(j, "email");
            String pw    = optString(j, "password");

            if (isBlank(name) || isBlank(email) || isBlank(pw)) {
                ctx.status(400).result("Missing name/email/password");
                return;
            }

            String subjects = j.has("subjects") && j.get("subjects").isJsonArray() ? GSON.toJson(j.getAsJsonArray("subjects")) : "[]";
            String tags = j.has("tags") && j.get("tags").isJsonArray() ? GSON.toJson(j.getAsJsonArray("tags")) : "[]";
            boolean onboarded = j.has("onboarded") && j.get("onboarded").getAsBoolean();

            try (ResultSet dup = findUserByEmail(email)) {
                if (dup != null && dup.next()) {
                    ctx.status(409).result("Email already used");
                    return;
                }
            }

            int id = createUser(name, email, pw, subjects, tags, onboarded);
            String token = UUID.randomUUID().toString();
            tokenToUser.put(token, id);

            try (ResultSet rs = findUserById(id)) {
                if (!rs.next()) { ctx.status(500).result("Created user not found"); return; }
                var out = new HashMap<>(rowToUserMap(rs));
                out.put("token", token);
                ctx.json(out);
            }
        });

        // ---------- LOGIN (fixed version) ----------
        app.post("/auth/login", ctx -> {
            JsonObject j = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String email = optString(j, "email");
            String pw    = optString(j, "password");

            try (ResultSet rs = findUserByEmail(email)) {
                if (rs == null || !rs.next()) {
                    System.out.println("❌ No such user: " + email);
                    ctx.status(401).result("No such user");
                    return;
                }

                String dbpw = rs.getString("password");
                System.out.println("🔎 Comparing login pw='" + pw + "' with db='" + dbpw + "'");
                if (dbpw != null) dbpw = dbpw.trim();
                if (pw != null) pw = pw.trim();

                if (!Objects.equals(pw, dbpw)) {
                    System.out.println("❌ Wrong password for: " + email);
                    ctx.status(401).result("Wrong password");
                    return;
                }

                int id = rs.getInt("id");
                String token = UUID.randomUUID().toString();
                tokenToUser.put(token, id);

                var out = new HashMap<>(rowToUserMap(rs));
                out.put("token", token);

                System.out.println("✅ Login success for: " + email);
                ctx.json(out);
            } catch (Exception e) {
                e.printStackTrace();
                ctx.status(500).result("Server error during login: " + e.getMessage());
            }
        });

        // ---------- PROFILE ----------
        app.get("/me", ctx -> {
            var me = auth(ctx.header("Authorization"));
            try (ResultSet rs = findUserById(me.id())) {
                if (!rs.next()) throw new NotFoundResponse();
                ctx.json(rowToUserMap(rs));
            }
        });

        app.put("/me", ctx -> {
            var me = auth(ctx.header("Authorization"));
            JsonObject j = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String subjects = j.has("subjects") ? GSON.toJson(j.getAsJsonArray("subjects")) : "[]";
            String tags     = j.has("tags") ? GSON.toJson(j.getAsJsonArray("tags")) : "[]";
            boolean onboarded = j.has("onboarded") && j.get("onboarded").getAsBoolean();
            updateProfile(me.id(), subjects, tags, onboarded);
            ctx.status(204);
        });

        app.put("/me/bio", ctx -> {
            var me = auth(ctx.header("Authorization"));
            JsonObject j = JsonParser.parseString(ctx.body()).getAsJsonObject();
            String bio = j.has("bio") && !j.get("bio").isJsonNull() ? j.get("bio").getAsString() : "";
            try (PreparedStatement ps = db.prepareStatement(
                    "UPDATE users SET bio=?, updated_at=? WHERE id=?")) {
                ps.setString(1, bio);
                ps.setLong(2, System.currentTimeMillis());
                ps.setInt(3, me.id());
                ps.executeUpdate();
            }
            ctx.status(204);
        });

        // ---------- USERS ----------
        app.get("/users", ctx -> {
            var me = auth(ctx.header("Authorization"));
            var list = new ArrayList<Map<String,Object>>();
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT id, name, email, onboarded FROM users ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Map.of(
                            "id", rs.getInt("id"),
                            "name", rs.getString("name"),
                            "email", rs.getString("email"),
                            "onboarded", rs.getInt("onboarded") == 1
                    ));
                }
            }
            ctx.json(list);
        });

        // ---------- CHAT ----------
        app.post("/rooms", ctx -> {
            var me = auth(ctx.header("Authorization"));
            int peerId = JsonParser.parseString(ctx.body()).getAsJsonObject().get("peerId").getAsInt();
            Room r = new Room(); r.id = roomSeq++; r.members.add(me.id); r.members.add(peerId);
            rooms.put(r.id, r);
            ctx.json(Map.of("roomId", r.id, "members", r.members));
        });

        app.post("/rooms/{rid}/messages", ctx -> {
            var me = auth(ctx.header("Authorization"));
            int rid = Integer.parseInt(ctx.pathParam("rid"));
            Room r = roomOr404(rid); requireMember(r, me.id);
            String text = JsonParser.parseString(ctx.body()).getAsJsonObject().get("text").getAsString();
            Message m = new Message(me.id, text);
            r.messages.add(m);
            broadcast(rid, GSON.toJson(Map.of("type", "chat", "userId", me.id, "text", text, "ts", m.ts)));
            ctx.status(204);
        });

        app.get("/rooms/{rid}/messages", ctx -> {
            var me = auth(ctx.header("Authorization"));
            int rid = Integer.parseInt(ctx.pathParam("rid"));
            String sinceStr = Optional.ofNullable(ctx.queryParam("since")).orElse("0");
            long since = Long.parseLong(sinceStr);
            Room r = roomOr404(rid); requireMember(r, me.id);
            var out = new ArrayList<Map<String, Object>>();
            for (Message m : r.messages) if (m.ts > since)
                out.add(Map.of("userId", m.userId, "text", m.text, "ts", m.ts));
            ctx.json(out);
        });

        app.ws("/ws/rooms/{rid}", ws -> {
            ws.onConnect(ctx -> {
                var me = authToken(ctx.queryParam("token"));
                int rid = Integer.parseInt(ctx.pathParam("rid"));
                Room r = roomOr404(rid); requireMember(r, me.id);
                ctx.attribute("userId", me.id);
                sockets.computeIfAbsent(rid, k -> new CopyOnWriteArraySet<>()).add(ctx);
                broadcast(rid, GSON.toJson(Map.of("type", "presence", "event", "join", "userId", me.id)));
            });
            ws.onMessage(ctx -> {
                int rid = Integer.parseInt(ctx.pathParam("rid"));
                Integer uid = ctx.attribute("userId");
                var j = JsonParser.parseString(ctx.message()).getAsJsonObject();
                if ("chat".equals(j.get("type").getAsString())) {
                    String text = j.get("text").getAsString();
                    rooms.get(rid).messages.add(new Message(uid, text));
                    broadcast(rid, GSON.toJson(Map.of("type", "chat", "userId", uid, "text", text, "ts", System.currentTimeMillis())));
                }
            });
            ws.onClose(ctx -> {
                int rid = Integer.parseInt(ctx.pathParam("rid"));
                Integer uid = ctx.attribute("userId");
                sockets.getOrDefault(rid, new CopyOnWriteArraySet<>()).remove(ctx);
                broadcast(rid, GSON.toJson(Map.of("type", "presence", "event", "leave", "userId", uid)));
            });
        });

        // ---------- ADMIN ROUTES ----------
        System.out.println("Mounting admin routes...");

        app.get("/admin/ping", ctx -> {
            requireAdmin(ctx.header("X-Admin"));
            ctx.result("ok");
        });

        app.get("/admin/users", ctx -> {
            requireAdmin(ctx.header("X-Admin"));
            var list = new ArrayList<Map<String,Object>>();
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT id, name, email, onboarded, created_at, updated_at FROM users ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Map.of(
                            "id", rs.getInt("id"),
                            "name", rs.getString("name"),
                            "email", rs.getString("email"),
                            "onboarded", rs.getInt("onboarded") == 1,
                            "created_at", rs.getLong("created_at"),
                            "updated_at", rs.getLong("updated_at")
                    ));
                }
            }
            ctx.json(list);
        });

        app.delete("/admin/users/{id}", ctx -> {
            requireAdmin(ctx.header("X-Admin"));
            int id = Integer.parseInt(ctx.pathParam("id"));
            try (PreparedStatement ps = db.prepareStatement("DELETE FROM users WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            tokenToUser.values().removeIf(v -> v == id);
            ctx.status(204);
        });

        app.post("/admin/reset", ctx -> {
            requireAdmin(ctx.header("X-Admin"));
            try (Statement st = db.createStatement()) {
                st.execute("DELETE FROM users");
                st.execute("VACUUM");
            }
            tokenToUser.clear();
            rooms.clear();
            sockets.clear();
            roomSeq = 1;
            ctx.status(204);
        });

        System.out.println("Admin routes mounted.");
    }

    static final String DB_PATH = System.getenv().getOrDefault(
            "BUDDYLINK_DB",
            new java.io.File("buddylink.db").getAbsolutePath()
    );

    static void initDb() throws Exception {
        db = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        createSchema();
        ensureBioColumn();
        boolean ok = isDbHealthy();
        if (!ok) {
            System.err.println("⚠ SQLite integrity_check failed. Recreating DB...");
            backupCorruptDb();
            db.close();
            db = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            createSchema();
            ensureBioColumn();
        }
    }

    static void createSchema() throws SQLException {
        try (Statement st = db.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL UNIQUE,
                    password TEXT NOT NULL,
                    subjects TEXT DEFAULT '[]',
                    tags TEXT DEFAULT '[]',
                    onboarded INTEGER DEFAULT 0,
                    bio TEXT DEFAULT '',
                    created_at INTEGER,
                    updated_at INTEGER
                );
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);");
        }
    }

    static void ensureBioColumn() {
        try (Statement s = db.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(users)")) {
            boolean hasBio = false;
            while (rs.next()) {
                if ("bio".equalsIgnoreCase(rs.getString("name"))) { hasBio = true; break; }
            }
            if (!hasBio) {
                try (Statement alter = db.createStatement()) {
                    alter.execute("ALTER TABLE users ADD COLUMN bio TEXT DEFAULT '';");
                }
            }
        } catch (SQLException ignore) {}
    }

    static boolean isDbHealthy() {
        try (Statement st = db.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA integrity_check;")) {
            return rs.next() && "ok".equalsIgnoreCase(rs.getString(1));
        } catch (SQLException e) { return false; }
    }

    static void backupCorruptDb() {
        try {
            String ts = String.valueOf(System.currentTimeMillis());
            var p = java.nio.file.Paths.get(DB_PATH);
            if (java.nio.file.Files.exists(p)) {
                var backup = java.nio.file.Paths.get(DB_PATH + ".corrupt-" + ts + ".db");
                java.nio.file.Files.move(p, backup);
                System.err.println("↪ Backed up corrupt DB to " + backup.getFileName());
            }
        } catch (Exception ignore) {}
    }

    static int createUser(String name, String email, String password, String subjectsJson, String tagsJson, boolean onboarded) throws Exception {
        String sql = "INSERT INTO users(name,email,password,subjects,tags,onboarded,bio,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long now = System.currentTimeMillis();
            ps.setString(1, name);
            ps.setString(2, email.toLowerCase());
            ps.setString(3, password);
            ps.setString(4, subjectsJson);
            ps.setString(5, tagsJson);
            ps.setInt(6, onboarded ? 1 : 0);
            ps.setString(7, "");
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys(); rs.next(); return rs.getInt(1);
        }
    }

    static ResultSet findUserByEmail(String email) throws Exception {
        PreparedStatement ps = db.prepareStatement("SELECT * FROM users WHERE email=?");
        ps.setString(1, email.toLowerCase());
        return ps.executeQuery();
    }

    static ResultSet findUserById(int id) throws Exception {
        PreparedStatement ps = db.prepareStatement("SELECT * FROM users WHERE id=?");
        ps.setInt(1, id);
        return ps.executeQuery();
    }

    static void updateProfile(int uid, String subjectsJson, String tagsJson, boolean onboarded) throws Exception {
        PreparedStatement ps = db.prepareStatement(
                "UPDATE users SET subjects=?, tags=?, onboarded=?, updated_at=? WHERE id=?");
        ps.setString(1, subjectsJson);
        ps.setString(2, tagsJson);
        ps.setInt(3, onboarded ? 1 : 0);
        ps.setLong(4, System.currentTimeMillis());
        ps.setInt(5, uid);
        ps.executeUpdate();
    }

    static Map<String, Object> rowToUserMap(ResultSet rs) throws Exception {
        return Map.of(
                "userId", rs.getInt("id"),
                "name", rs.getString("name"),
                "email", rs.getString("email"),
                "subjects", JsonParser.parseString(rs.getString("subjects")),
                "tags", JsonParser.parseString(rs.getString("tags")),
                "onboarded", rs.getInt("onboarded") == 1,
                "bio", Optional.ofNullable(rs.getString("bio")).orElse("")
        );
    }

    static String optString(JsonObject j, String key) {
        return (j.has(key) && !j.get(key).isJsonNull()) ? j.get(key).getAsString() : null;
    }
    static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    record UserCtx(int id, String name) {}
    static UserCtx auth(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new UnauthorizedResponse();
        return authToken(header.substring(7));
    }
    static UserCtx authToken(String token) {
        Integer uid = tokenToUser.get(token);
        if (uid == null) throw new UnauthorizedResponse();
        try (ResultSet rs = findUserById(uid)) {
            if (!rs.next()) throw new UnauthorizedResponse();
            return new UserCtx(uid, rs.getString("name"));
        } catch (Exception e) { throw new UnauthorizedResponse(); }
    }
    static Room roomOr404(int rid) {
        Room r = rooms.get(rid); if (r == null) throw new NotFoundResponse(); return r;
    }
    static void requireMember(Room r, int uid) { if (!r.members.contains(uid)) throw new UnauthorizedResponse(); }
    static void broadcast(int rid, String json) {
        sockets.getOrDefault(rid, new CopyOnWriteArraySet<>()).forEach(ws -> ws.send(json));
    }
}
