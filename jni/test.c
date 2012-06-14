#include "net.h"

int main()
{
	int fd = socket_tcpconnect4("127.0.0.1", 53, -1);
	if (fd == -1) {
		printf("socket_tcpconnect4 127.0.0.1:53 fail %d %s\n", errno, strerror(errno));
		return 1;
	}
	char buf[1024];
	memset(buf, 0, sizeof(buf));
	int len = snprintf(buf+2, sizeof(buf)-2, "xudejianQ");
	buf[0] = 0x04;
	buf[1] = 0x01;
	len += 2;
	int rv = socket_send(fd, buf, len);
	if (rv != len) {
		printf("socket_send fail %d %s\n", errno, strerror(errno));
		return 1;
	}

	do {
		memset(buf, 0, sizeof(buf));
		rv = socket_recv(fd, buf, sizeof(buf));
		if (rv == -1 && errno == EAGAIN) {
			usleep(100000);
			continue;
		}
		if (rv < 1 && errno != EAGAIN) {
			printf("socket_recv fail %d %s\n", errno, strerror(errno));
			return 1;
		}
		printf("rv=%d buf=%s\n", rv, buf);
	} while (1);
	close(fd);
	return 0;
}

