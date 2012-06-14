#!/bin/sh
cd ..
ndk-build $@ || exit 1

for i in libs/*;
do
	if [ -f $i/dnsproxy ]; then
		mv -f $i/dnsproxy $i/libdnslite.so
	fi
done
