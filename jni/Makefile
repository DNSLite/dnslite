CC=gcc
CFLAGS= -O3 -W -Wall -D_REENTRANT -DNDEBUG #-g
LDFLAGS= #-g
LDLIBS=-lssl -lcrypto
CXXFLAGS=$(CFLAGS)
OBJS=net.o dnsproxy.o

LIBO=dnsproxy test

all	: $(LIBO)

dnsproxy: net.o dns.o nameserver.o cache.o dnsproxy.o
	$(CXX) -o $@ $^ $(LDFLAGS) $(LDLIBS)

test:test.o net.o
	$(CC) -o $@ $^ $(LDFLAGS) $(LDLIBS)

clean:
	@-rm -f *.o $(LIBO) *~
tags:
	ctags *.h *.c *.cpp

