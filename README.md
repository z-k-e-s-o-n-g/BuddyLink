Installation guide:
first download the lib file with packaged jar. Next put that lib file into a folder called BuddyLink. Open up terminal.
if on macOs:
cd ~/Downloads/BuddyLink
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export BUDDYLINK_API=http://10.153.194.210:7070
"$JAVA_HOME/bin/java" \
  --class-path "lib/*" \
  com.example.BuddyLink.MainApplication

If on linux:
cd ~/Downloads/BuddyLink
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export BUDDYLINK_API=http://10.153.194.210:7070

"$JAVA_HOME/bin/java" \
  --class-path "lib/*" \
  com.example.BuddyLink.MainApplication
If on windows:
cd "$env:USERPROFILE\Downloads\BuddyLink"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:BUDDYLINK_API = "http://10.153.194.210:7070"

& "$env:JAVA_HOME\bin\java.exe" `
  --class-path "lib/*" `
  com.example.BuddyLink.MainApplication


To launch.
