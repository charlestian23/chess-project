package ch.teemoo.bobby.gui;

import static ch.teemoo.bobby.models.Board.SIZE;

import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.ListIterator;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

import ch.teemoo.bobby.helpers.BotFactory;
import ch.teemoo.bobby.helpers.GuiHelper;
import ch.teemoo.bobby.models.games.GameSetup;
import ch.teemoo.bobby.models.moves.Move;
import ch.teemoo.bobby.models.Position;
import ch.teemoo.bobby.models.pieces.Bishop;
import ch.teemoo.bobby.models.pieces.Knight;
import ch.teemoo.bobby.models.pieces.Piece;
import ch.teemoo.bobby.models.pieces.Queen;
import ch.teemoo.bobby.models.pieces.Rook;
import ch.teemoo.bobby.models.players.Human;
import ch.teemoo.bobby.models.players.Player;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ReviewGameView extends JFrame implements IBoardView {

    private static final Border NO_BORDER = BorderFactory.createEmptyBorder();
    private static final Border BLUE_BORDER = BorderFactory.createLineBorder(java.awt.Color.blue, 3, true);

    private final boolean visible;
    private final Container contentPane;
    private final Icon logoIcon;
    private final GuiHelper guiHelper;
    private Square[][] squares = new Square[SIZE][SIZE];

    private JMenuItem itemNew;
    private JMenuItem itemSave;
    private JMenuItem itemLoad;
    private JMenuItem itemPrintToConsole;
    private JMenuItem itemAbout;

    private JMenuItem itemOpenGame;


    @Override
    public Square[][] getSquares() {
        return new Square[0][];
    }

    @Override
    public void setItemOpenGameActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemNewActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemSaveActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemLoadActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemPrintToConsoleActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemSuggestMoveActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemUndoMoveActionListener(ActionListener actionListener) {

    }

    @Override
    public void setItemProposeDrawActionListener(ActionListener actionListener) {

    }

    @Override
    public void display(Piece[][] positions, boolean isReversed) {

    }

    @Override
    public void refresh(Piece[][] positions) {

    }

    @Override
    public void resetAllClickables() {

    }

    @Override
    public void cleanSquaresBorder() {

    }

    @Override
    public void addBorderToLastMoveSquares(Move move) {

    }

    @Override
    public Optional<File> saveGameDialog() {
        return Optional.empty();
    }

    @Override
    public Optional<File> loadGameDialog() {
        return Optional.empty();
    }

    @Override
    public GameSetup gameSetupDialog(BotFactory botFactory, boolean exitOnCancel) {
        return null;
    }

    @Override
    public Piece promotionDialog(Color color) {
        return null;
    }

    @Override
    public void popupInfo(String message) {

    }

    @Override
    public void popupError(String message) {

    }
}
