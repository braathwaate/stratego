PKG=com/cjmalloy/stratego
VER=stratego_v0.9.2.jar

all: $(VER) 

foo:
	cd $(PKG); javac -g server/shared/*.java
	cd $(PKG); javac -g *.java
#	cd $(PKG); javac -g server/remote/*.java
	cd $(PKG); javac -g player/*.java
	cd $(PKG); javac -g server/*.java

$(VER): foo
	jar cfm $(VER) Manifest.txt images $(PKG)/*.class $(PKG)/player/*.class $(PKG)/server/shared/*.class $(PKG)/resource
	chmod +x $(VER)

clean:
	rm -f $(VER)
	rm -f $(PKG)/*.class
	rm -f $(PKG)/player/*.class
	rm -f $(PKG)/server/*.class
	rm -f $(PKG)/server/shared/*.class



