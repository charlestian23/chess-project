package ch.teemoo.bobby.models.database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.sql.*;

import org.apache.ibatis.jdbc.ScriptRunner;

public class Database
{
    private static final String URL = "jdbc:mysql://localhost";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";

    private static final String QUERY_LOCATIONS = "src/main/resources/sql/";

    public static void createDatabase()
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            ScriptRunner scriptRunner = new ScriptRunner(connection);
            Reader reader = new BufferedReader(new FileReader(QUERY_LOCATIONS + "create.sql"));
            scriptRunner.runScript(reader);
            connection.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static int getNextID()
    {
        int id = -1;
        try
        {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select max(id) + 1 as next_id from games");
            resultSet.next();
            if (resultSet != null)
                id = resultSet.getInt("next_id");
            connection.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return id;
    }

    public static void addMove(int id, int move_number, String move)
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            String insert = "insert into game (id, move_number, move) values (?, ?, ?)";
            PreparedStatement insertStatement = connection.prepareStatement(insert);
            insertStatement.setInt(1, id);
            insertStatement.setInt(2, move_number);
            insertStatement.setString(3, move);
            insertStatement.execute();
            connection.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void addPlayers(int id, String whitePlayer, String blackPlayer)
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            String insert = "insert into players (id, white_player, black_player) values (?, ?, ?)";
            PreparedStatement insertStatement = connection.prepareStatement(insert);
            insertStatement.setInt(1, id);
            insertStatement.setString(2, whitePlayer);
            insertStatement.setString(3, blackPlayer);
            insertStatement.execute();
            connection.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
