PKG=com/cjmalloy/stratego
VER=stratego_v0.11.1

all: $(VER).zip

foo:
	cd $(PKG); javac -g server/shared/*.java
	cd $(PKG); javac -g *.java
#	cd $(PKG); javac -g server/remote/*.java
	cd $(PKG); javac -g player/*.java
	cd $(PKG); javac -g server/*.java

$(VER).zip: foo
	jar cfm $(VER).jar Manifest.txt images $(PKG)/*.class $(PKG)/player/*.class $(PKG)/server/shared/*.class $(PKG)/resource
	chmod +x $(VER).jar
	zip $(VER).zip $(VER).jar

clean:
	rm -f $(VER).zip
	rm -f $(VER).jar
	rm -f $(PKG)/*.class
	rm -f $(PKG)/player/*.class
	rm -f $(PKG)/server/*.class
	rm -f $(PKG)/server/shared/*.class



