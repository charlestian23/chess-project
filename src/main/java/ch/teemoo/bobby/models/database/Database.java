package ch.teemoo.bobby.models.database;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Reader;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.jdbc.ScriptRunner;

// References: https://stackoverflow.com/questions/30651830/use-jdbc-mysql-connector-in-intellij-idea
public class Database
{
    private static final String URL = "jdbc:mysql://localhost";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";

    private static final String QUERY_LOCATIONS = "src/main/resources/sql/";
    private static final String SERVER_NAME = "bobby_chess";

    public static void createDatabase()
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
            PreparedStatement statement = connection.prepareStatement("create database if not exists " + SERVER_NAME);
            statement.execute();
            connection.close();

            connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
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
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select max(id) + 1 as next_id from players");
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
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
            String insert = "insert into games (id, move_number, move) values (?, ?, ?)";
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

    public static void removeMove(int id, int move_number)
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
            String insert = "delete from games where id = ? and move_number = ?";
            PreparedStatement insertStatement = connection.prepareStatement(insert);
            insertStatement.setInt(1, id);
            insertStatement.setInt(2, move_number);
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
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
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

    public static List<Map<String, String>> getPlayers()
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
            String getData = "select * from players";
            PreparedStatement getDataStatement = connection.prepareStatement(getData);
            ResultSet resultSet = getDataStatement.executeQuery(getData);

            List<Map<String, String>> data = new LinkedList<>();

            while (resultSet.next())
            {
                Map<String, String> row = new HashMap<>();
                row.put("id", resultSet.getString("id"));
                row.put("white_player", resultSet.getString("white_player"));
                row.put("black_player", resultSet.getString("black_player"));
                data.add(row);
            }

            return data;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static List<Map<String, String>> getMovesFromID(int id)
    {
        try
        {
            Connection connection = DriverManager.getConnection(URL + "/" + SERVER_NAME, USERNAME, PASSWORD);
            String getData = "select * from games where id = " + id;
            PreparedStatement getDataStatement = connection.prepareStatement(getData);
            ResultSet resultSet = getDataStatement.executeQuery(getData);

            List<Map<String, String>> data = new LinkedList<>();

            while (resultSet.next())
            {
                Map<String, String> row = new HashMap<>();
                row.put("id", resultSet.getString("id"));
                row.put("move_number", String.valueOf(resultSet.getInt("move_number")));
                row.put("move", resultSet.getString("move"));
                data.add(row);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
