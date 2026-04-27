@echo off
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/utils/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/auth/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/user/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/customer/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/room/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/booking/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/hotelservice/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/promotion/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/interaction/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/report/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/backend/Main.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/api/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/utils/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/view/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/view/admin/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/view/staff/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/view/customer/*.java
javac -encoding utf-8 -cp "lib/*;build/classes" -sourcepath src -d build/classes src/quanlykhachsan/frontend/MainUI.java
echo Compile Completed!!
