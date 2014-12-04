#!/bin/sh
for i in libs/*;
do
	if [ -f $i/dnsproxy ]; then
		mv -f $i/dnsproxy $i/libdnslite.so
	fi
done
