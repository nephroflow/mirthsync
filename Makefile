help:
	@echo
	@echo 'Usage: make {mirthsync|help}'
	@echo
	@echo 'Targets:'
	@echo '  mirthsync         Create executable mirthsync jar file.'
	@echo

clean:
	rm -f mirthsync
	lein clean

build: clean
	lein uberjar

mirthsync: build
	echo '#!/usr/bin/env bash' > mirthsync
	echo 'java $$JVM_OPTS -jar "$$0" "$$@"' >> mirthsync
	echo 'exit' >> mirthsync
	cat target/uberjar/mirthsync*-standalone.jar >> mirthsync
	chmod 0755 mirthsync
	@echo "*** Done. Created mirthsync executable: ./boot ***"