package me.xu.tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

public class DNSProxyClient {

	private final static String TAG = "DNSClient";
	private final String TCP_HOST = "127.0.0.1";
	private final int SERVER_PORT = 53;
	private final int CONN_TIMEOUT = 200;
	private Socket socket = null;
	private DataOutputStream write = null;
	private BufferedReader read = null;

	public final static String cmd_QUIT = "xudejianQ";
	public final static String cmd_SUCC = "xudejianS";
	public final static String cmd_LOGS = "xudejianL";
	public final static String cmd_GETP = "xudejianP";
	public final static String cmd_RESET_DNS = "xudejianR";

	public final static String cmd_QUIT_BACK = "QUIT";
	public final static String cmd_SUCC_BACK = "SUCC";
	public final static String cmd_LOGS_BACK = "LOGS";

	public boolean connect() {
		close();
		socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(TCP_HOST, SERVER_PORT),
					CONN_TIMEOUT);
			setSoTimeout(CONN_TIMEOUT);
		} catch (ConnectException refused) {
			//Log.i(TAG, refused.getMessage());
			close();
			return false;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			close();
			return false;
		}

		try {
			write = new DataOutputStream(socket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			close();
			return false;
		}
		try {
			read = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
		} catch (IOException e) {
			e.printStackTrace();
			close();
			return false;
		}
		return true;
	}

	public boolean setSoTimeout(int timeout) {
		if (socket == null) {
			return false;
		}
		try {
			socket.setSoTimeout(timeout);
			return true;
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void close() {
		if (write != null) {
			try {
				write.close();
			} catch (IOException e) {
			}
			write = null;
		}
		if (read != null) {
			try {
				read.close();
			} catch (IOException e) {
			}
			read = null;
		}
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
			}
			socket = null;
		}
	}

	private boolean isServerAlive() {
		String rv = sendCmd(cmd_SUCC);
		if (rv != null && rv.equals(cmd_SUCC_BACK)) {
			return true;
		}
		return false;
	}

	public boolean re_set_netdns() {
		String rv = sendCmd(cmd_RESET_DNS);
		if (rv != null && rv.equals(cmd_SUCC_BACK)) {
			return true;
		}
		return false;
	}

	public String getLog() throws IOException {
		return read.readLine();
	}

	public boolean preGetLog() {
		String rv = sendCmd(cmd_LOGS);
		if (rv != null && rv.equals(cmd_LOGS_BACK)) {
			return true;
		}
		return false;
	}

//	private int getpid() {
//		String rv = sendCmd(cmd_GETP);
//		if (rv != null && rv.startsWith("PID=")) {
//			String pid = rv.substring(4);
//			return Integer.valueOf(pid);
//		}
//		return 0;
//	}

	private boolean sendQuit() {
		String rv = sendCmd(cmd_QUIT);
		close();
		if (rv != null && rv.equals(cmd_QUIT_BACK)) {
			return true;
		}
		return false;
	}

	private boolean sendCmdOnly(String cmd) {
		byte[] cmd_cont = cmd.getBytes();
		int len = cmd_cont.length + 2;
		byte[] buf = new byte[len];
		buf[0] = 0x04;
		buf[1] = 0x01;
		for (int i = 0; i < cmd_cont.length; ++i) {
			buf[i + 2] = cmd_cont[i];
		}
		try {
			write.write(buf);
			write.flush();
			return true;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			return false;
		}
	}

	private String sendCmd(String cmd) {
		if (sendCmdOnly(cmd)) {
			try {
				return read.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static boolean isDnsRuning() {
		DNSProxyClient dnsc = new DNSProxyClient();
		try {
			if (dnsc.connect()) {
				return dnsc.isServerAlive();
			}
		} finally {
			dnsc.close();
		}
		return false;
	}

	public static boolean quit() {
		DNSProxyClient dnsc = new DNSProxyClient();
		try {
			if (dnsc.connect()) {
				return dnsc.sendQuit();
			}
		} finally {
			dnsc.close();
		}
		return false;
	}

	public static boolean re_set_dns() {
		DNSProxyClient dnsc = new DNSProxyClient();
		try {
			if (dnsc.connect()) {
				return dnsc.re_set_netdns();
			}
		} finally {
			dnsc.close();
		}
		return false;
	}
}
