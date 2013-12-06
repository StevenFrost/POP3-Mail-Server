import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.IllegalBlockingModeException;

public class Pop3Server {
	/* System Messages */
	private static final String ERROR_INVALID_NUMBER_OF_ARGUMENTS = "An invalid number of arguments were specified. Usage: java Pop3Server port [timeout].";
	private static final String ERROR_INVALID_ARGUMENT = "An invalid argument was specified.";
	private static final String ERROR_INVALID_PORT = "An invalid port was specified. Port must be between 0 and 65535 inclusive.";
	private static final String ERROR_INVALID_TIMEOUT = "An invalid timeout was specified. Timeout must be greater than zero.";
	private static final String ERROR_UNABLE_TO_ESTABLISH_SOCKET = "An error occurred while establishing a socket or thread.";
	public static final int ERROR_STATUS = 1;

	/* POP3 Server Properties */
	private int port;
	private int timeout;
	private boolean serverRunning;

	/**
	 * Initial POP3 Server setup.
	 * 
	 * @param port
	 *            the port number to run the server from
	 * @param timeout
	 *            the default timeout of each client connection, in seconds
	 */
	public Pop3Server(int port, int timeout) throws IllegalArgumentException {
		this.port = port;
		this.timeout = timeout;

		/* Check the timeout range entered */
		if (timeout <= 0) {
			throw new IllegalArgumentException(ERROR_INVALID_TIMEOUT);
		}
		
		/* Check the port range entered */
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException(ERROR_INVALID_PORT);
		}
	}

	/**
	 * Runs the POP3 server. A socket is opened to listen on the port specified
	 * and then the function waits for incoming connections on that port. Any
	 * new incoming connection triggers a new ServerThread which directs client
	 * input through the CommandInterpreter.
	 */
	public void run() {
		serverRunning = true;

		/*
		 * Attempt to open a ServerSocket. Also closes the socket when the
		 * try/catch is complete
		 */
		try (ServerSocket socket = new ServerSocket(port)) {
			while (serverRunning) {
				/*
				 * Create and start a new ServerThread. A reference to the
				 * thread isn't needed as the Garbage Collector will clean it up
				 * after the client quits or the session times out.
				 */
				new ServerThread(socket.accept(), timeout).start();
			}
		} catch (IOException | SecurityException | IllegalBlockingModeException
				| IllegalArgumentException ex) {
			System.err.println(ERROR_UNABLE_TO_ESTABLISH_SOCKET);
		} finally {
			/* Close the database connection */
			IDatabase db = Database.getInstance();
			db.close();
			db = null;
		}
	}

	/**
	 * Main Pop3Server entry point
	 * 
	 * @param args
	 *            command line arguments. Expected: Pop3Server port [timeout]
	 */
	public static void main(String[] args) {
		int port = 110, timeout = 600;

		try {
			/* Try to parse the arguments passed to the server */
			if (args.length == 1) {
				port = Integer.parseInt(args[0]);
			} else if (args.length == 2) {
				port = Integer.parseInt(args[0]);
				timeout = Integer.parseInt(args[1]);
			} else {
				System.err.println(ERROR_INVALID_NUMBER_OF_ARGUMENTS);
				System.exit(ERROR_STATUS);
			}

			/* Run the server */
			Pop3Server server = new Pop3Server(port, timeout);
			server.run();
		} catch (NumberFormatException e) {
			System.err.println(ERROR_INVALID_ARGUMENT);
			System.exit(ERROR_STATUS);
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.exit(ERROR_STATUS);
		}
	}
}
