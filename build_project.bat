@echo off
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/model/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/dao/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/daoimpl/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/controller/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/Main.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/api/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/view/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/utils/*.java
javac -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/MainUI.java
echo Compile Completed!!
