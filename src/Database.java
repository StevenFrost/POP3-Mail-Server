import java.sql.*;

public class Database implements IDatabase {
	/* Database members */
	private ResultSet result;
	private Connection connection;
	private PreparedStatement query;
	private static Database instance = null;

	/* Connection settings */
	private static final String DB_USERNAME = "xxxxxxx";
	private static final String DB_PASSWORD = "xxxxxxx";
	private static final String DB_URL = "jdbc:mysql://localhost/xxxxxx";

	/* System Messages */
	private static final String ERROR_CONNECTION = "Database connection error.";
	private static final String ERROR_TIMEOUT = "The database connection timed out.";
	private static final String ERROR_QUERY = "Error while querying the database";
	private static final String ERROR_CLOSE = "An error occurred while closing the database connection.";

	/* SQL Queries */
	private static final String QUERY_USER_EXISTS = "SELECT `vchUsername` FROM `m_Maildrop` WHERE `vchUsername` = ?";
	private static final String QUERY_PASSWORD = "SELECT `vchPassword` FROM `m_Maildrop` WHERE `vchUsername` = ? AND `vchPassword` = ?";
	private static final String QUERY_UPDATE_LOCK = "UPDATE `m_Maildrop` SET `tiLocked` = ? WHERE `vchUsername` = ?";
	private static final String QUERY_MAILDROP_LOCKED = "SELECT `tiLocked` FROM `m_Maildrop` WHERE `vchUsername` = ? AND `tiLocked` = 1";
	private static final String QUERY_DELETE_MARKED = "DELETE `mail` FROM `m_Mail` AS `mail` NATURAL JOIN `m_Maildrop` AS `maildrop` WHERE `vchUsername` = ? AND `markedForDeletion` = 1";
	private static final String QUERY_NUM_MARKED_UNMARKED = "SELECT COUNT(*) AS 'numMsg' FROM `m_Mail` NATURAL JOIN `m_Maildrop` WHERE `vchUsername` = ?";
	private static final String QUERY_NUM_UNMARKED = "SELECT COUNT(*) AS 'numMsg' FROM `m_Mail` NATURAL JOIN `m_Maildrop` WHERE `vchUsername` = ? AND `markedForDeletion` = 0";
	private static final String QUERY_MAILDROP_SIZE = "SELECT SUM(LENGTH(txMailContent)) AS 'maildropSize' FROM `m_Mail` NATURAL JOIN `m_Maildrop` WHERE `vchUsername` = ? AND `markedForDeletion` = 0";
	private static final String QUERY_MESSAGE_SIZE = "SELECT LENGTH(txMailContent) AS 'messageSize' FROM (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable WHERE `rowNum` = ?";
	private static final String QUERY_MESSAGE_EXISTS = "SELECT `rowNum` FROM (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable WHERE `rowNum` = ? AND `markedForDeletion` = 0";
	private static final String QUERY_UPDATE_MARK = "UPDATE `m_Mail` NATURAL JOIN (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable SET `markedForDeletion` = ? WHERE `rowNum` = ?";
	private static final String QUERY_MESSAGE_MARKED = "SELECT `markedForDeletion` FROM (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable WHERE `rowNum` = ? AND `markedForDeletion` = 1";
	private static final String QUERY_MESSAGE_CONTENT = "SELECT `txMailContent` FROM (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable WHERE `rowNum` = ?";
	private static final String QUERY_MESSAGE_UIDL = "SELECT `vchUIDL` FROM (SELECT *, @rowNum := @rowNum + 1 rowNum FROM `m_Mail` NATURAL JOIN `m_Maildrop`, (SELECT @rowNum := 0) AS m WHERE `vchUsername` = ? ORDER BY iMailID) AS idTable WHERE `rowNum` = ?";
	private static final String QUERY_UPDATE_RESTORE = "UPDATE `m_Mail` NATURAL JOIN `m_Maildrop` SET `markedForDeletion` = 0 WHERE `vchUsername` = ? AND `markedForDeletion` = 1";
	private static final String QUERY_UPDATE_ALL_MAILDROP = "UPDATE `m_Maildrop` SET `tiLocked` = 0";

	/**
	 * Default constructor
	 */
	private Database() {
		try {
			DriverManager.registerDriver(new org.gjt.mm.mysql.Driver());
			connection = DriverManager.getConnection(DB_URL, DB_USERNAME,
					DB_PASSWORD);
			unlockUsers();
		} catch (SQLException e) {
			System.err.println(ERROR_CONNECTION);
			System.exit(Pop3Server.ERROR_STATUS);
		}
	}

	/**
	 * Gets the singleton instance of the database
	 * 
	 * @return the instance of the database
	 */
	public static synchronized Database getInstance() {
		if (instance == null) {
			instance = new Database();
		}
		return instance;
	}

	/**
	 * Unlocks all maildrops in the database. This is used when the server first
	 * starts to clean any incomplete sessions.
	 */
	private void unlockUsers() {
		try {
			query = connection.prepareStatement(QUERY_UPDATE_ALL_MAILDROP);
			query.executeUpdate();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
	}

	@Override
	public boolean userExists(String username) {
		try {
			/* Attempt to find the username in the database */
			query = connection.prepareStatement(QUERY_USER_EXISTS);
			query.setString(1, username);
			return query.executeQuery().next();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return false;
	}

	@Override
	public boolean passwordCorrect(String username, String password) {
		try {
			/* Attempt to get the password from the database */
			query = connection.prepareStatement(QUERY_PASSWORD);
			query.setString(1, username);
			query.setString(2, password);
			return query.executeQuery().next();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return false;
	}

	@Override
	public boolean getMaildropLocked(String username) {
		try {
			/* Check if the user's maildrop is locked */
			query = connection.prepareStatement(QUERY_MAILDROP_LOCKED);
			query.setString(1, username);
			return query.executeQuery().next();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return true;
	}

	@Override
	public void setMaildropLocked(String username, boolean locked) {
		try {
			/* Update the lock on the user's maildrop */
			query = connection.prepareStatement(QUERY_UPDATE_LOCK);
			query.setInt(1, locked ? 1 : 0);
			query.setString(2, username);
			query.executeUpdate();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
	}

	@Override
	public int deleteMarkedMessages(String username) {
		int numDeleted = 0;

		try {
			/* Delete messages marked for the specified user */
			query = connection.prepareStatement(QUERY_DELETE_MARKED);
			query.setString(1, username);
			query.executeUpdate();

			/* Get the number of messages deleted */
			numDeleted = query.getUpdateCount();
			return (numDeleted != -1) ? numDeleted : 0;
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return numDeleted;
	}

	@Override
	public int numMessages(String username, boolean deleted) {
		try {
			/* Get the number of messages in the user's maildrop */
			query = connection
					.prepareStatement(deleted ? QUERY_NUM_MARKED_UNMARKED
							: QUERY_NUM_UNMARKED);
			query.setString(1, username);
			result = query.executeQuery();
			if (!result.next()) {
				return 0;
			}

			/* Parse the result */
			return Integer.parseInt(result.getString("numMsg"));
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		} catch (NumberFormatException e) {
			return 0;
		}
		return 0;
	}

	@Override
	public int sizeOfMaildrop(String username) {
		try {
			/* Get the size of the user's maildrop, excluding deleted */
			query = connection.prepareStatement(QUERY_MAILDROP_SIZE);
			query.setString(1, username);
			result = query.executeQuery();
			if (!result.next()) {
				return 0;
			}

			/* Parse the result */
			return Integer.parseInt(result.getString("maildropSize"));
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		} catch (NumberFormatException e) {
			return 0;
		}
		return 0;
	}

	@Override
	public int sizeOfMessage(String username, int id) {
		try {
			/* Get the size of the specified message */
			query = connection.prepareStatement(QUERY_MESSAGE_SIZE);
			query.setString(1, username);
			query.setInt(2, id);
			result = query.executeQuery();
			if (!result.next()) {
				return 0;
			}

			/* Parse the result */
			return Integer.parseInt(result.getString("messageSize"));
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		} catch (NumberFormatException e) {
			return 0;
		}
		return 0;
	}

	@Override
	public boolean messageExists(String username, int id) {
		try {
			/* Checks if a message exists and is not marked for deletion */
			query = connection.prepareStatement(QUERY_MESSAGE_EXISTS);
			query.setString(1, username);
			query.setInt(2, id);
			return query.executeQuery().next();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return false;
	}

	@Override
	public void setMark(String username, int id, boolean marked) {
		try {
			/* Set the marked for deletion state of the message */
			query = connection.prepareStatement(QUERY_UPDATE_MARK);
			query.setString(1, username);
			query.setInt(2, marked ? 1 : 0);
			query.setInt(3, id);
			query.executeUpdate();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
	}

	@Override
	public boolean messageMarked(String username, int id) {
		try {
			/* Get the marked status of the message */
			query = connection.prepareStatement(QUERY_MESSAGE_MARKED);
			query.setString(1, username);
			query.setInt(2, id);
			return query.executeQuery().next();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return false;
	}

	@Override
	public String getMessage(String username, int id) {
		try {
			/* Get the message content from the database */
			query = connection.prepareStatement(QUERY_MESSAGE_CONTENT);
			query.setString(1, username);
			query.setInt(2, id);

			/* Result the result */
			ResultSet result = query.executeQuery();
			if (result.next()) {
				return result.getString("txMailContent");
			}
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return null;
	}

	@Override
	public String messageUIDL(String username, int id) {
		try {
			/* Get the message content from the database */
			query = connection.prepareStatement(QUERY_MESSAGE_UIDL);
			query.setString(1, username);
			query.setInt(2, id);

			/* Result the result */
			ResultSet result = query.executeQuery();
			if (result.next()) {
				return result.getString("vchUIDL");
			}
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
		return null;
	}

	@Override
	public void restoreMarked(String username) {
		try {
			/* Set the marked for deletion state of the message */
			query = connection.prepareStatement(QUERY_UPDATE_RESTORE);
			query.setString(1, username);
			query.executeUpdate();
		} catch (SQLTimeoutException e) {
			System.err.println(ERROR_TIMEOUT);
		} catch (SQLException e) {
			System.err.println(ERROR_QUERY + ": " + e.getMessage());
		}
	}

	@Override
	public void close() {
		try {
			query = null;
			result = null;
			connection.close();
		} catch (SQLException e) {
			System.err.println(ERROR_CLOSE);
		}
	}
}
