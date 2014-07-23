package me.xu.tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Locale;

import android.util.Log;

public class Sudo {
	private static String TAG = "Sudo";
	private Process suProc = null;

	private BufferedReader suOut = null;

	private DataOutputStream suIn = null;
	private String last_mount_dir = null;
	private String last_mount_device = null;
	private String chmod = null;

	static {
		System.loadLibrary("util");
	}

	public static native String getprop(String name);

	public BufferedReader getSuOut() {
		return suOut;
	}

	public static boolean isRootedDevice() {
		String[] arrayOfString = new String[8];
		arrayOfString[0] = "/sbin/";
		arrayOfString[1] = "/system/bin/";
		arrayOfString[2] = "/system/xbin/";
		arrayOfString[3] = "/data/local/xbin/";
		arrayOfString[4] = "/data/local/bin/";
		arrayOfString[5] = "/system/sd/xbin/";
		arrayOfString[6] = "/system/bin/failsafe/";
		arrayOfString[7] = "/data/local/";
		int j = arrayOfString.length;
		for (int i = 0; i < j; i++) {
			if (new File(arrayOfString[i] + "su").exists()) {
				return true;
			}
		}
		return false;
	}

	public static String getPath(String bin) {
		String[] arrayOfString = new String[8];
		arrayOfString[0] = "/sbin/";
		arrayOfString[1] = "/system/bin/";
		arrayOfString[2] = "/system/xbin/";
		arrayOfString[3] = "/data/local/xbin/";
		arrayOfString[4] = "/data/local/bin/";
		arrayOfString[5] = "/system/sd/xbin/";
		arrayOfString[6] = "/system/bin/failsafe/";
		arrayOfString[7] = "/data/local/";
		int j = arrayOfString.length;
		for (int i = 0; i < j; i++) {
			if (new File(arrayOfString[i] + bin).exists()) {
				return arrayOfString[i];
			}
		}
		return null;
	}

	public static String getSuPath() {
		return getPath("su");
	}

	public boolean lastRunSuccess() {
		String rv = null;
		try {
			while ((rv = suOut.readLine()) != null) {
				if (rv.equals("0")) {
					return true;
				} else if (rv.equals("1")) {
					return false;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean runcommand(String cmd) {
		try {
			writeBytes(cmd);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean mountRw(String dir) {
		last_mount_dir = dir;
		last_mount_device = null;
		boolean succ = false;
		boolean rw = false;
		try {

			skipOut();
			String rv;
			runcommand("mount && echo 0 || echo 1\n");
			while ((rv = suOut.readLine()) != null) {
				String[] inf = rv.split("\\s+");
				if (inf.length == 6) {
					if (inf[1].equals("on")) {
						if (inf[2].equals(dir)) {
							last_mount_device = inf[0];
							if (inf[5].equals("rw")) {
								rw = true;
								break;
							} else if (inf[5].indexOf(',') != -1) {
								String[] op = inf[5].split("[\\(|,|\\)]");
								for (String s : op) {
									if (s.equals("rw")) {
										rw = true;
										break;
									}
								}
							}
							if (rw) {
								break;
							}
						}
					} else if (inf[1].equals(dir)) {
						last_mount_device = inf[0];
						if (inf[3].equals("rw")) {
							rw = true;
							break;
						} else if (inf[3].indexOf(',') != -1) {
							String[] op = inf[3].split("[\\(|,|\\)]");
							for (String s : op) {
								if (s.equals("rw")) {
									rw = true;
									break;
								}
							}
						}
					}
				} else if (rv.equals("0")) {
					break;
				} else if (rv.equals("1")) {
					break;
				}

				if (rw) {
					break;
				}
			} // end while

			if (!rw) {
				skipOut();
				if (last_mount_device != null) {
					runcommand("mount -o rw,remount " + last_mount_device + " "
							+ dir + " && echo $? || echo $?\n");
				} else {
					runcommand("mount -o rw,remount " + dir
							+ " && echo $? || echo $?\n");
				}
				while ((rv = suOut.readLine()) != null) {
					if (rv.equals("0")) {
						succ = true;
						break;
					} else if (rv.equals("1")) {
						succ = false;
						break;
					}
				}
			} else {
				succ = true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return succ;
	}

	public boolean remountRw() {
		boolean succ = false;
		if (last_mount_dir == null) {
			return false;
		}
		try {
			String rv;
			skipOut();
			if (last_mount_device == null) {
				runcommand("mount -o rw,remount " + last_mount_dir
						+ " && echo $? || echo $?\n");
			} else {
				runcommand("mount -o rw,remount " + last_mount_device + " "
						+ last_mount_dir + " && echo $? || echo $?\n");
			}
			while ((rv = suOut.readLine()) != null) {
				if (rv.equals("0")) {
					succ = true;
					break;
				} else if (rv.equals("1")) {
					succ = false;
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return succ;
	}

	public boolean mountRo(String dir) {
		last_mount_dir = dir;
		last_mount_device = null;
		boolean succ = false;
		boolean ro = false;
		try {
			String rv;
			skipOut();
			runcommand("mount && echo $? || echo $?\n");
			while ((rv = suOut.readLine()) != null) {
				String[] inf = rv.split("\\s+");
				if (inf.length == 6) {
					if (inf[1].equals("on")) {
						if (inf[2].equals(dir)) {
							last_mount_device = inf[0];
							if (inf[5].equals("rw")) {
								ro = true;
								break;
							} else if (inf[5].indexOf(',') != -1) {
								String[] op = inf[5].split("[\\(|,|\\)]");
								for (String s : op) {
									if (s.equals("ro")) {
										ro = true;
										break;
									}
								}
							}
							if (ro) {
								break;
							}
						}
					} else if (inf[1].equals(dir)) {
						last_mount_device = inf[0];
						if (inf[3].equals("ro")) {
							ro = true;
							break;
						} else if (inf[3].indexOf(',') != -1) {
							String[] op = inf[3].split("[\\(|,|\\)]");
							for (String s : op) {
								if (s.equals("ro")) {
									ro = true;
									break;
								}
							}
						}
					}

					if (ro) {
						break;
					}
				} else if (rv.equals("0")) {
					break;
				} else if (rv.equals("1")) {
					break;
				}
			} // end while

			if (!ro) {
				skipOut();
				if (last_mount_device != null) {
					runcommand("mount -o ro,remount " + last_mount_device + " "
							+ last_mount_dir + " && echo $? || echo $?\n");
				} else {
					runcommand("mount -o ro,remount " + last_mount_dir
							+ " && echo $? || echo $?\n");
				}
				while ((rv = suOut.readLine()) != null) {
					if (rv.equals("0")) {
						succ = true;
						break;
					} else if (rv.equals("1")) {
						succ = false;
						break;
					}
				}
			} else {
				// Log.d("mountRo", "already ro");
				succ = true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return succ;
	}

	public boolean remountRo() {
		if (last_mount_dir == null) {
			return false;
		}
		boolean succ = false;
		try {
			String rv;
			skipOut();
			if (last_mount_device == null) {
				runcommand("mount -o ro,remount " + last_mount_dir
						+ " && echo $? || echo $?\n");
			} else {
				runcommand("mount -o ro,remount " + last_mount_device + " "
						+ last_mount_dir + " && echo $? || echo $?\n");
			}
			while ((rv = suOut.readLine()) != null) {
				if (rv.equals("0")) {
					succ = true;
					break;
				} else if (rv.equals("1")) {
					succ = false;
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return succ;
	}

	public boolean prepareSuProc() {
		close();
		try {
			ProcessBuilder builder = new ProcessBuilder("su");
			builder.redirectErrorStream(true);
			suProc = builder.start();
			suOut = new BufferedReader(new InputStreamReader(
					suProc.getInputStream()));
			suIn = new DataOutputStream(suProc.getOutputStream());
			if (!runcommand("echo $?;echo xdjEND\n")) {
				return false;
			}
			String rv;
			while ((rv = suOut.readLine()) != null) {
				if (rv.toLowerCase(Locale.US).indexOf("permission denied") != -1) {
					close();
					return false;
				} else if (rv.toLowerCase(Locale.US).indexOf("not allowed to su") != -1) {
					close();
					return false;
				} else if (rv.equals("0")) {
					return true;
				} else if (rv.equals("1")) {
					return false;
				} else if (rv.equals("xdjEND")) {
					break;
				} else {
					System.err.println(rv);
					break;
				}
			}

			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void writeBytes(String cmd) throws IOException {
		suIn.writeBytes(cmd);
	}

	public void skipOut() {
		if (suOut != null) {
			try {
				runcommand("echo XDJ_END\n");
				String rv;
				while ((rv = suOut.readLine()) != null) {
					if (rv.equals("XDJ_END")) {
						return;
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void close() {
		if (suIn != null) {
			try {
				suIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			suIn = null;
		}
		if (suOut != null) {
			try {
				suOut.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			suOut = null;
		}
		if (suProc != null) {
			suProc.destroy();
			suProc = null;
		}
	}

	public static boolean suSaveHosts(String str) {

		boolean rv = false;
		String[] lines = str.split("\n");
		Sudo sudo = new Sudo();

		if (sudo.prepareSuProc()) {
			if (sudo.mountRw("/system")) {
				try {
					sudo.writeBytes("chmod 644 /system/etc/hosts\n");
					sudo.writeBytes("echo > /system/etc/hosts\n");
					if (lines != null) {
						for (String line : lines) {
							line = line.replace('"', ' ');
							line = line.replace('\'', ' ');
							line = line.replace('\\', ' ');
							line = line.trim();

							sudo.writeBytes("echo '" + line
									+ "' >> /system/etc/hosts\n");
						}
					}
					sudo.remountRo();
					sudo.writeBytes("exit\n");
					rv = true;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		sudo.close();
		return rv;
	}

	public static String getMountInfo(String mountDir) {
		File mount = new File("/proc/mounts");
		if (!mount.exists()) {
			return null;
		}
		BufferedReader input = null;

		try {
			input = new BufferedReader(new FileReader(mount));
			String rv = null;
			while ((rv = input.readLine()) != null) {
				String[] inf = rv.split("\\s+");
				if (inf.length == 6) {
					if (inf[1].equals("on")) {
						if (inf[2].equals(mountDir)) {
							return rv;
						}
					} else if (inf[1].equals(mountDir)) {
						return rv;
					}
				}
			}
			return null;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static boolean isMountHasProp(String mountInfo, String prop) {
		String[] inf = mountInfo.split("\\s+");
		if (inf.length == 6) {
			if (inf[1].equals("on")) {
				if (inf[5].equalsIgnoreCase(prop)) {
					return true;
				} else if (inf[5].indexOf(',') != -1) {
					String[] op = inf[5].split("[\\(|,|\\)]");
					for (String s : op) {
						if (s.equalsIgnoreCase(prop)) {
							return true;
						}
					}
				}
			} else {
				if (inf[3].equalsIgnoreCase(prop)) {
					return true;
				} else if (inf[3].indexOf(',') != -1) {
					String[] op = inf[3].split("[\\(|,|\\)]");
					for (String s : op) {
						if (s.equalsIgnoreCase(prop)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	public static boolean isMountRw(String mountInfo) {
		return isMountHasProp(mountInfo, "rw");
	}

	public static boolean isMountRo(String mountInfo) {
		return isMountHasProp(mountInfo, "ro");
	}

	public static boolean sudoCommand(String[] param) {
		boolean rv = false;
		Sudo sudo = new Sudo();
		if (sudo.prepareSuProc()) {
			try {
				for (int i = 0; i < param.length; ++i) {
					sudo.writeBytes(param[i] + "\n");
					sudo.skipOut();
				}
				sudo.skipOut();
				sudo.writeBytes("exit\n");
				rv = true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sudo.close();
		return rv;
	}

	public static boolean setProperties(String[] names, String[] vals) {
		if (names == null || vals == null || names.length != vals.length) {
			return false;
		}

		Sudo sudo = new Sudo();

		if (sudo.prepareSuProc()) {
			String cmd;
			try {
				for (int i = 0; i < names.length; ++i) {
					if (vals[i].trim().length() < 1) {
						cmd = "setprop " + names[i]
								+ " 127.0.0.1 && echo SETOK || exit\n";
					} else {
						cmd = "setprop " + names[i] + " " + vals[i]
								+ " && echo SETOK || exit\n";
					}
					sudo.writeBytes(cmd);
					sudo.skipOut();
				}
				sudo.writeBytes("exit\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		sudo.close();
		return true;
	}

	public static String[] getProperties(String[] names) {
		if (names.length < 1) {
			return null;
		}
		String[] vals = new String[names.length];

		for (int i = 0; i < names.length; ++i) {
			if (names[i].length() < 1) {
				vals[i] = "";
				continue;
			}
			vals[i] = getprop(names[i]);
		}
		return vals;
	}

	public String getCmdChmod(String file, int mval) {
		if (chmod == null) {
			chmod = getPath("chmod");
			if (chmod != null) {
				chmod += "chmod";
			}
		}
		if (chmod == null) {
			return null;
		}
		return chmod + " " + mval + " " + file;
	}
}
