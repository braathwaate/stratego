PKG=com/cjmalloy/stratego
VER=stratego_v0.1.jar

all: $(VER)

$(VER):
	jar cfm $(VER) Manifest.txt images $(PKG)/*.class $(PKG)/player/*.class $(PKG)/server/shared/*.class $(PKG)/resource

clean:
	rm -f $(VER)
	rm -f $(PKG)/*.class
	rm -f $(PKG)/player/*.class
	rm -f $(PKG)/server/*.class
	rm -f $(PKG)/server/shared/*.class



