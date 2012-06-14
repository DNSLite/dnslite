CC=gcc
CFLAGS= -O3 -W -Wall -D_REENTRANT -DNDEBUG #-g
LDFLAGS= -pthread #-g
LDLIBS= -lpthread
CXXFLAGS=$(CFLAGS)
OBJS=net.o dnsproxy.o

LIBO=dnsproxy test

all	: $(LIBO)

dnsproxy: net.o dnsproxy.o
	$(CXX) -o $@ $^ $(LDFLAGS) $(LDLIBS)

test:test.o net.o
	$(CC) -o $@ $^ $(LDFLAGS) $(LDLIBS)

clean:
	@-rm -f *.o $(LIBO) *~
tags:
	ctags *.h *.c *.cpp

