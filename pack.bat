mkdir out
%JAVA_HOME%\bin\javac -d out src/com/nnapz/*.java
cd out
echo Main-Class: com.nnapz.CountGifColors > manifest.txt
%JAVA_HOME%\bin\jar cvfm ..\counter.jar manifest.txt  com\nnapz\*.class
cd ..
