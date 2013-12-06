public class CommandInterpreter implements ICommandInterpreter {
	/* Interpreter messages */
	private static final String INVALID_IN_STATE 		= "-ERR command invalid in the current state";
	private static final String INVALID_ARG_TYPE 		= "-ERR invalid argument type";
	private static final String TOO_MANY_ARGS 			= "-ERR too many command arguments";
	private static final String TOO_FEW_ARGS 			= "-ERR too few command arguments";
	private static final String INCORRECT_NUM_ARGS 		= "-ERR incorrect number of arguments";
	private static final String INVALID_COMMAND 		= "-ERR invalid command";
	private static final String USER_OK 				= "+OK found user account";
	private static final String USER_LOCKED 			= "-ERR the maildrop is currently locked";
	private static final String USER_NOT_FOUND 			= "-ERR user not found";
	private static final String USER_COMMAND_NOT_SENT 	= "-ERR USER command not sent";
	private static final String PASSWORD_OK 			= "+OK user authorised";
	private static final String PASSWORD_INCORRECT 		= "-ERR password incorrect";
	private static final String QUIT_OK 				= "+OK quitting";
	private static final String NOOP_OK 				= "+OK no operation";
	private static final String MESSAGE_NOT_FOUND 		= "-ERR message not found";
	private static final String MESSAGE_ALREADY_DELETED = "-ERR message already deleted";
	private static final String MESSAGE_MARKED 			= "+OK message marked as deleted";
	private static final String RESET_OK 				= "+OK deleted messages restored";
	private static final String INVALID_ARG_VAL 		= "-ERR invalid argument value";
	private static final String QUIT_ERROR 				= "-ERR some messages were not deleted";

	private State state;
	private IDatabase database;
	private String username;

	private enum State {
		AUTHORIZATION, TRANSACTION, UPDATE
	};
	
	/**
	 * Default constructor
	 */
	public CommandInterpreter() {
		state = State.AUTHORIZATION;
		database = Database.getInstance();
		username = "";
	}

	public String handleInput(String input) {
		input = input.replaceAll("[\r\n]+$", "");
		String[] cmdArgs = input.split(" ", 2);
		String in = " " + input;
		
		switch (cmdArgs[0].toUpperCase()) {
		case "USER":
			return commandUSER(in, cmdArgs);
		case "PASS":
			return commandPASS(in, cmdArgs);
		case "QUIT":
			return commandQUIT(in, cmdArgs);
		case "STAT":
			return commandSTAT(in, cmdArgs);
		case "LIST":
			return commandLIST(in, cmdArgs);
		case "RETR":
			return commandRETR(in, cmdArgs);
		case "DELE":
			return commandDELE(in, cmdArgs);
		case "NOOP":
			return commandNOOP(in, cmdArgs);
		case "RSET":
			return commandRSET(in, cmdArgs);
		case "TOP":
			return commandTOP(in, cmdArgs);
		case "UIDL":
			return commandUIDL(in, cmdArgs);
		default:
			return INVALID_COMMAND + in;
		}
	}

	/**
	 * Handles the USER part of POP3 account authentication. The function
	 * returns an error status for calling the command in an incorrect
	 * interpreter state, passing an incorrect username, passing too few
	 * arguments or passing too many arguments (as a username is assumed to not
	 * allow spaces in it).
	 * 
	 * @param cmd
	 *            the command, split into the identifier and arguments
	 * @return the server response for the command
	 */
	private String commandUSER(String input, String[] cmd) {
		if (state != State.AUTHORIZATION) {
			return INVALID_IN_STATE + input;
		} else if ((cmd.length != 2) || (cmd[1].split(" ").length != 1)) {
			return INCORRECT_NUM_ARGS + input;
		}

		if (database.userExists(cmd[1])) {
			if (!database.getMaildropLocked(cmd[1])) {
				username = cmd[1];
				return USER_OK + input;
			} else {
				return USER_LOCKED + input;
			}
		} else {
			return USER_NOT_FOUND + input;
		}
	}

	/**
	 * Handles the PASS part of POP3 account authentication. Passwords can
	 * contain spaces so any text after the 'PASS' text is interpreted as the
	 * user's password.
	 * 
	 * Error statuses are returned if the command is called in an invalid state,
	 * the entered password is incorrect, too few arguments are passed or the
	 * 'USER' command has not been executed successfully.
	 * 
	 * @param cmd
	 *            the command, split into the identifier and arguments
	 * @return the server response for the command
	 */
	private String commandPASS(String input, String[] cmd) {
		if (state != State.AUTHORIZATION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 2) {
			return INCORRECT_NUM_ARGS + input;
		} else if (username.equals("")) {
			return USER_COMMAND_NOT_SENT + input;
		}

		if (database.passwordCorrect(username, cmd[1])) {
			state = State.TRANSACTION;
			database.setMaildropLocked(username, true);
			return PASSWORD_OK + input;
		} else {
			return PASSWORD_INCORRECT + input;
		}
	}

	/**
	 * Handles the QUIT command in the Authorisation and Transaction states. In
	 * the Authorisation state, executing the QUIT command simply closes the
	 * connection. In the Transaction state, the interpreter must switch to the
	 * Update state and any messages marked to be deleted must be removed from
	 * the user's maildrop.
	 * 
	 * @param cmd
	 *            the command, no arguments are expected
	 * @return the server response for the command
	 */
	private String commandQUIT(String input, String[] cmd) {
		if (cmd.length != 1) {
			return INCORRECT_NUM_ARGS + input;
		}

		if (state == State.AUTHORIZATION) {
			return QUIT_OK + input;
		} else {
			state = State.UPDATE;
			return performUpdate() + input;
		}
	}

	/**
	 * Handles the NOOP part of the POP3 specification. This function simply
	 * returns a success message unless it is triggered in the incorrect state
	 * or has arguments.
	 * 
	 * @param cmd
	 *            the command, split into the identifier and arguments
	 * @return the server response for the command
	 */
	private String commandNOOP(String input, String[] cmd) {
		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 1) {
			return INCORRECT_NUM_ARGS + input;
		} else {
			return NOOP_OK + input;
		}
	}

	/**
	 * Handles the STAT command in the Transaction state. The command returns an
	 * error status if the command is called in an invalid state or if too many
	 * arguments are passed.
	 * 
	 * The command returns a response of the number of messages in the maildrop
	 * along with their total size in octets. These figures do not include
	 * messages marked as deleted.
	 * 
	 * @param cmd
	 *            the command, no arguments are expected
	 * @return the server response for the command
	 */
	private String commandSTAT(String input, String[] cmd) {
		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 1) {
			return INCORRECT_NUM_ARGS + input;
		}

		return "+OK " + database.numMessages(username, false) + " "
				+ database.sizeOfMaildrop(username);
	}

	/**
	 * Handles the LIST command in the Transaction state. An error status is
	 * returned if the command is called in an incorrect state, too many
	 * arguments are provided, an argument had the wrong type or the specified
	 * message could not be found.
	 * 
	 * If an argument is paired with the command, it represents the message to
	 * list data for. If no argument is provided, a listing of all commands
	 * should be returned as a multiline response, terminated by a full-stop.
	 * 
	 * @param cmd
	 *            the command with arguments
	 * @return the server response for the command
	 */
	private String commandLIST(String input, String[] cmd) {
		int id;

		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length > 2) {
			return TOO_MANY_ARGS + input;
		}

		if (cmd.length == 1) {
			int numMessages = database.numMessages(username, true);
			String out = "+OK " + database.numMessages(username, false) + " ("
					+ database.sizeOfMaildrop(username) + ")\r\n";
			for (int i = 1; i <= numMessages; i++) {
				if (!database.messageMarked(username, i)) {
					out += i + " " + database.sizeOfMessage(username, i) + "\r\n";
				}
			}
			return out + ".";
		} else {
			try {
				id = Integer.parseInt(cmd[1]);
			} catch (Exception ex) {
				return INVALID_ARG_TYPE + input;
			}

			if (database.messageExists(username, id) && !database.messageMarked(username, id)) {
				return "+OK " + id + " " + database.sizeOfMessage(username, id);
			} else {
				return MESSAGE_NOT_FOUND + input;
			}
		}
	}

	/**
	 * Handles the RETR command in the Transaction state. An error status is
	 * returned if the command is called in an incorrect state, too many/few
	 * arguments are provided, an argument has the wrong type or the specified
	 * message could not be found.
	 * 
	 * The argument paired with the command represents the message to retrieve.
	 * 
	 * @param cmd
	 *            the command with arguments
	 * @return the server response for the command
	 */
	private String commandRETR(String input, String[] cmd) {
		int id;

		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 2) {
			return INCORRECT_NUM_ARGS + input;
		}

		try {
			id = Integer.parseInt(cmd[1]);
		} catch (NumberFormatException ex) {
			return INVALID_ARG_TYPE + input;
		}

		if (!database.messageExists(username, id)) {
			return MESSAGE_NOT_FOUND + input;
		} else if (database.messageMarked(username, id)) {
			return MESSAGE_ALREADY_DELETED + input;
		} else {
			return "+OK " + database.sizeOfMessage(username, id) + " octets\r\n"
					+ database.getMessage(username, id) + "\r\n.";
		}
	}

	/**
	 * Handles the DELE command in the Transaction state. An error status is
	 * returned if the command is called in an incorrect state, arguments are
	 * provided with the wrong type, or the specified message could not be
	 * found.
	 * 
	 * The argument paired with the command represents the message to retrieve.
	 * 
	 * @param cmd
	 *            the command, with the id of the message to be deleted
	 * @return the server response for the command
	 */
	private String commandDELE(String input, String[] cmd) {
		int id;

		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 2) {
			return INCORRECT_NUM_ARGS + input;
		}

		try {
			id = Integer.parseInt(cmd[1]);
		} catch (NumberFormatException ex) {
			return INVALID_ARG_TYPE + input;
		}

		if (!database.messageExists(username, id)) {
			return MESSAGE_NOT_FOUND + input;
		} else if (database.messageMarked(username, id)) {
			return MESSAGE_ALREADY_DELETED + input;
		} else {
			database.setMark(username, id, true);
			return MESSAGE_MARKED + input;
		}
	}

	/**
	 * Handles the RESET command in the transaction state. An error status is
	 * returned if the command is called in an invalid state, or if arguments
	 * are passed.
	 * 
	 * @param cmd
	 *            the command, no arguments are expected
	 * @return the server response for the command
	 */
	private String commandRSET(String input, String[] cmd) {
		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length != 1) {
			return INCORRECT_NUM_ARGS + input;
		} else {
			database.restoreMarked(username);
			return RESET_OK + input;
		}
	}

	/**
	 * Handles the TOP command in the transaction state. An error status is
	 * returned if the command is called in an invalid state, an incorrect
	 * number of arguments are provided, the arguments have an incorrect type or
	 * the message couldn't be found.
	 * 
	 * @param cmd
	 *            the command, along with arguments
	 * @return the server response for the command
	 */
	private String commandTOP(String input, String[] cmd) {
		int id, n;
		String[] args;

		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length < 2) {
			return TOO_FEW_ARGS + input;
		} else {
			args = cmd[1].split(" ");
			if (args.length != 2) {
				return INCORRECT_NUM_ARGS + input;
			}
		}

		try {
			id = Integer.parseInt(args[0]);
			n = Integer.parseInt(args[1]);
		} catch (NumberFormatException ex) {
			return INVALID_ARG_TYPE + input;
		}

		if (!database.messageExists(username, id)) {
			return MESSAGE_NOT_FOUND + input;
		} else if (database.messageMarked(username, id)) {
			return MESSAGE_ALREADY_DELETED + input;
		} else if (n < 0) {
			return INVALID_ARG_VAL + input;
		} else {
			String message = database.getMessage(username, id);
			
			/* Check for an empty message */
			if (message.equals(null)) {
				return "+OK\r\n.";
			}
			
			/* Split the full message into header and body components */
			String[] tmp = message.split("\n\n", 2);
			String header = tmp[0];
			String body = tmp[1];
			
			/* Split the body into lines */
			tmp = body.split("\n");
			body = "";
			
			/* Reconstruct the body for n lines */
			for (int i = 0; i < tmp.length - 1; i++) {
				if (n == i) break;
				body += "\n" + tmp[i];
			}
			
			/* Return the requested lines */
			return "+OK \r\n" + header + "\r\n" + body + "\r\n.";
		}
	}

	/**
	 * Handles the UIDL command in the transaction state. An error status is
	 * returned if the command was called in an invalid state, if an incorrect
	 * number of arguments were provided or if the specified message couldn't be
	 * found.
	 * 
	 * @param cmd
	 *            the command, along with arguments
	 * @return the server response for the command
	 */
	private String commandUIDL(String input, String[] cmd) {
		int id;

		if (state != State.TRANSACTION) {
			return INVALID_IN_STATE + input;
		} else if (cmd.length > 2) {
			return TOO_MANY_ARGS + input;
		}

		if (cmd.length == 1) {
			int numMessages = database.numMessages(username, false);
			String out = "+OK " + numMessages + " ("
					+ database.sizeOfMaildrop(username) + ")\r\n";
			for (int i = 1; i <= numMessages; i++) {
				out += i + " " + database.messageUIDL(username, i) + "\r\n";
			}
			return out + ".";
		} else {
			try {
				id = Integer.parseInt(cmd[1]);
			} catch (NumberFormatException ex) {
				return INVALID_ARG_TYPE + input;
			}

			if (database.messageExists(username, id) && !database.messageMarked(username, id)) {
				return "+OK " + id + " " + database.messageUIDL(username, id);
			} else {
				return MESSAGE_NOT_FOUND + input;
			}
		}
	}

	/**
	 * Deletes any messages marked to be deleted and returns the result from the
	 * operation
	 * 
	 * @return +OK if all messages were deleted, -ERR otherwise
	 */
	private String performUpdate() {
		if (state != State.UPDATE) {
			return "-ERR cannot delete outside of UPDATE state";
		}

		/* Delete marked messages */
		int numMessagesBeforeDelete = database.numMessages(username, true);
		int n = database.deleteMarkedMessages(username);
		
		/* Check how many messages were deleted */
		if (n == (numMessagesBeforeDelete - database.numMessages(username, true))) {
			database.setMaildropLocked(username, false);
			return "+OK " + n + " messages deleted";
		} else {
			return QUIT_ERROR;
		}
	}
	
	@Override
	public void close() {
		database.restoreMarked(username);
		database.setMaildropLocked(username, false);
	}
}
