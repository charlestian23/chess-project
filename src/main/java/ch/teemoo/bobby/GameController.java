package ch.teemoo.bobby;

import static ch.teemoo.bobby.helpers.ColorHelper.swap;
import static ch.teemoo.bobby.models.Board.SIZE;

import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.border.Border;

import ch.teemoo.bobby.gui.IBoardView;
import ch.teemoo.bobby.gui.Square;
import ch.teemoo.bobby.helpers.BotFactory;
import ch.teemoo.bobby.helpers.GameFactory;
import ch.teemoo.bobby.models.Board;
import ch.teemoo.bobby.models.Color;
import ch.teemoo.bobby.models.database.Database;
import ch.teemoo.bobby.models.games.Game;
import ch.teemoo.bobby.models.games.GameResult;
import ch.teemoo.bobby.models.games.GameSetup;
import ch.teemoo.bobby.models.games.GameState;
import ch.teemoo.bobby.models.moves.Move;
import ch.teemoo.bobby.models.moves.PromotionMove;
import ch.teemoo.bobby.models.pieces.Piece;
import ch.teemoo.bobby.models.pieces.Queen;
import ch.teemoo.bobby.models.players.Bot;
import ch.teemoo.bobby.models.players.Player;
import ch.teemoo.bobby.services.FileService;
import ch.teemoo.bobby.services.MoveService;
import ch.teemoo.bobby.services.PortableGameNotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameController {
	private final static Logger logger = LoggerFactory.getLogger(GameController.class);

	private static final Border RED_BORDER = BorderFactory.createLineBorder(java.awt.Color.red, 3, true);
	private static final Border BLUE_BORDER = BorderFactory.createLineBorder(java.awt.Color.blue, 3, true);

	private Consumer<GameResult> gameResultConsumer;

	private final IBoardView view;
	private Board board;
	private Game game;
	private final GameFactory gameFactory;
	private final BotFactory botFactory;
	private final MoveService moveService;
	private final FileService fileService;
	private final PortableGameNotationService portableGameNotationService;
	private final Bot botToSuggestMove;
	private final boolean showTiming = true;

	private Square selectedSquare = null;

	private int gameID;

	public GameController(IBoardView view, GameFactory gameFactory, BotFactory botFactory, MoveService moveService,
		FileService fileService, PortableGameNotationService portableGameNotationService) {
		this.moveService = moveService;
		this.fileService = fileService;
		this.portableGameNotationService = portableGameNotationService;
		this.view = view;
		this.gameFactory = gameFactory;
		this.botFactory = botFactory;
		this.botToSuggestMove = botFactory.getStrongestBot();
		initView(gameFactory.emptyGame().getBoard());
	}

	void initView(Board board) {
		refreshBoardView(board);
		view.setItemNewActionListener(actionEvent -> {
			newGame(null, false, r -> {});
		});
		view.setItemOpenGameActionListener(actionEvent -> openGame());
		view.setItemLoadActionListener(actionEvent -> loadGame());
		view.setItemSaveActionListener(actionEvent -> saveGame());
		view.setItemPrintToConsoleActionListener(actionEvent -> printGameToConsole());
		view.setItemSuggestMoveActionListener(actionEvent -> suggestMove());
		view.setItemUndoMoveActionListener(actionEvent -> undoLastMove());
		view.setItemProposeDrawActionListener(actionEvent -> requestDraw());
	}

	public void newGame(GameSetup gameSetup, boolean exitOnCancel, Consumer<GameResult> gameResultConsumer) {
		if (gameSetup == null) {
			GameSetup gameSetupFromDialog = view.gameSetupDialog(botFactory, exitOnCancel);
			if (gameSetupFromDialog == null && !exitOnCancel) {
				return;
			} else {
				gameSetup = gameSetupFromDialog;
			}
		}
		this.gameID = Database.getNextID();
		this.game = gameFactory.createGame(gameSetup);

		Database.addPlayers(this.gameID, this.game.getWhitePlayer().getName(), this.game.getBlackPlayer().getName());

		this.board = game.getBoard();
		this.gameResultConsumer = gameResultConsumer;
		refreshBoardView(board);
		cleanSelectedSquare();
		if (game.canBePlayed()) {
			play();
		}
	}

	void refreshBoardView(Board board) {
		boolean isReversed = false;
		if (game != null && game.canBePlayed() && game.getWhitePlayer().isBot()) {
			isReversed = true;
		}
		view.display(board.getBoard(), isReversed);
	}

	void play() {
		SwingUtilities.invokeLater(this::playNextMove);
	}

	void playNextMove() {
		while (game.getPlayerToPlay().isBot() && !isGameOver(game)) {
			Player player = game.getPlayerToPlay();
			if (!(player instanceof Bot)) {
				throw new RuntimeException("Player has to be a bot");
			}
			Bot bot = (Bot) player;
			Instant start = Instant.now();

			FindBestMoveTask findBestMoveTask = new FindBestMoveTask(bot, game);
			findBestMoveTask.execute();
			Move move;

			try {
				move = findBestMoveTask.get();
			} catch (InterruptedException | ExecutionException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Move computation failed", e);
			}
			Instant end = Instant.now();
			if (showTiming) {
				logger.debug("Time to select move: {}", Duration.between(start, end));
			}
			doMove(move);
		}

		if (!game.getPlayerToPlay().isBot() && !isGameOver(game)) {
			view.resetAllClickables();
			markSquaresClickableByColor(game.getToPlay());
		}
	}

	void doMove(Move move) {
		Player player = game.getPlayerByColor(move.getPiece().getColor());
		view.cleanSquaresBorder();
		if (!player.isBot()) {
			cleanSelectedSquare();
			view.resetAllClickables();
		}

		List<Move> matchingMoves = moveService
			.computeMoves(board, move.getPiece(), move.getFromX(), move.getFromY(), game.getHistory(), true, false)
			.stream().filter(m -> m.equalsForPositions(move)).collect(Collectors.toList());
		Move allowedMove = getAllowedMove(move, player, matchingMoves);
		// We use allowedMove instead of given move since it contains additional info like taking and check
		board.doMove(allowedMove);
		view.refresh(board.getBoard());
		view.addBorderToLastMoveSquares(allowedMove);
		info(allowedMove.getPrettyNotation(), false);

		game.addMoveToHistory(allowedMove);
		Database.addMove(this.gameID, game.getTotalMoves(), allowedMove.getBasicNotation());

		game.setToPlay(swap(allowedMove.getPiece().getColor()));
		displayGameInfo(allowedMove);
	}

	Move getAllowedMove(Move move, Player player, List<Move> matchingMoves) {
		List<Move> allowedMoves;
		if (!matchingMoves.isEmpty() && matchingMoves.stream().allMatch(m -> m instanceof PromotionMove)) {
			Piece promotedPiece;
			if (move instanceof PromotionMove) {
				promotedPiece = ((PromotionMove) move).getPromotedPiece();
			} else {
				if (player.isBot()) {
					promotedPiece = new Queen(move.getPiece().getColor());
				} else {
					promotedPiece = view.promotionDialog(move.getPiece().getColor());
				}
			}
			allowedMoves = matchingMoves.stream()
				.filter(m -> ((PromotionMove) m).getPromotedPiece().getClass().equals(promotedPiece.getClass()))
				.collect(Collectors.toList());
		} else {
			allowedMoves = matchingMoves;
		}
		if (allowedMoves.isEmpty()) {
			throw new RuntimeException("Unauthorized move: " + move.getBasicNotation());
		}
		if (allowedMoves.size() > 1) {
			throw new RuntimeException(
				"Ambiguous move: " + move.getBasicNotation() + ". Multiple moves possible here: " + allowedMoves
					.toString());
		}
		return allowedMoves.get(0);
	}

	void undoLastMove(Move move) {
		Player player = game.getPlayerByColor(move.getPiece().getColor());
		if (!player.isBot()) {
			cleanSelectedSquare();
			view.cleanSquaresBorder();
			view.resetAllClickables();
		}

		board.undoMove(move);
		Database.removeMove(this.gameID, game.getTotalMoves());

		view.refresh(board.getBoard());
		info("Undo: " + move.getPrettyNotation(), false);
		game.removeLastMoveFromHistory();
		game.setToPlay(move.getPiece().getColor());
	}

	void displayGameInfo(Move move) {
		boolean showPopup = !game.getWhitePlayer().isBot() || !game.getBlackPlayer().isBot();
		GameState state;
		if (game.getState() != null && !game.getState().isInProgress()) {
			state = game.getState();
		} else {
			state = moveService.getGameState(game.getBoard(), game.getToPlay(), game.getHistory());
		}
		switch (state) {
			case LOSS:
				Color winningColor = move.getPiece().getColor();
				Player winner = game.getPlayerByColor(winningColor);
				Player loser;
				if (winningColor == Color.WHITE)
					loser = game.getPlayerByColor(Color.BLACK);
				else
					loser = game.getPlayerByColor(Color.WHITE);

				if (winningColor == Color.WHITE) {
					info("1-0" + getNbMovesInfo(game), false);
					gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.WHITE_WINS));
				} else {
					info("0-1" + getNbMovesInfo(game), false);
					gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.BLACK_WINS));
				}
				if (winner.isBot()) {
					info("Checkmate! Ha ha, not even Spassky could beat me!", showPopup);
				} else {
					if (loser.isBot())
						info(
							"Checkmated?!? Noooo! How is this possible?\nCongrats, not everybody is able to beat the genius Bobby!",
							showPopup);
					else
						info(winner.getName() + " wins!", showPopup);
				}
				break;
			case DRAW_STALEMATE:
				info("1/2-1/2" + getNbMovesInfo(game), false);
				info("Draw (Stalemate). The game is over.", showPopup);
				gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.DRAW));
				break;
			case DRAW_50_MOVES:
				info("1/2-1/2" + getNbMovesInfo(game), false);
				info("Draw (50 moves). The game is over.", showPopup);
				gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.DRAW));
				break;
			case DRAW_THREEFOLD:
				info("1/2-1/2" + getNbMovesInfo(game), false);
				info("Draw (threefold). The game is over.", showPopup);
				gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.DRAW));
				break;
			case DRAW_AGREEMENT:
				info("1/2-1/2" + getNbMovesInfo(game), false);
				info("Draw (agreement). The game is over.", showPopup);
				gameResultConsumer.accept(new GameResult(game.getHistory().size(), GameResult.Result.DRAW));
				break;
			case IN_PROGRESS:
			default:
				if (move.isChecking()) {
					info("Check!", showPopup);
				}
				break;
		}
	}

	private String getNbMovesInfo(Game game) {
		return " (" + game.getHistory().size() + " moves)";
	}

	private void markSquaresClickableByColor(Color color) {
		Square[][] squares = view.getSquares();
		for (int i = 0; i < SIZE; i++) {
			for (int j = 0; j < SIZE; j++) {
				Square square = squares[i][j];
				Piece piece = square.getPiece();
				if (piece != null && piece.getColor() == color) {
					markSquareClickable(square);
				}
			}
		}
	}

	private void markSquareClickable(Square square) {
		if (square.getMouseListeners().length == 0) {
			square.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					try {
						squareClicked(square);
					} catch (Exception exception) {
						error(exception, true);
					}
				}

				public void mouseEntered(MouseEvent e) {
					square.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				}

				public void mouseExited(MouseEvent e) {
					square.setCursor(Cursor.getDefaultCursor());
				}
			});
		}
	}

	private void squareClicked(Square square) {
		if (selectedSquare != null) {
			if (selectedSquare == square) {
				// cancel current selection
				view.cleanSquaresBorder();
				cleanSelectedSquare();
				view.resetAllClickables();
				markSquaresClickableByColor(game.getToPlay());
			} else {
				doMove(new Move(selectedSquare.getPiece(), selectedSquare.getPosition().getX(), selectedSquare.getPosition().getY(), square.getPosition().getX(), square.getPosition().getY()));
				play();
			}
		} else {
			if (square.getPiece() != null) {
				if (square.getPiece().getColor() == game.getToPlay()) {
					selectedSquare = square;
					view.cleanSquaresBorder();
					view.resetAllClickables();
					// Self piece is clickable so that it selection can be cancelled
					markSquareClickable(square);
					square.setBorder(RED_BORDER);
					List<Move> moves = moveService.computeMoves(board, square.getPiece(), square.getPosition().getX(),
						square.getPosition().getY(), game.getHistory(), true, false);
					for (Move move : moves) {
						Square destination = view.getSquares()[move.getToY()][move.getToX()];
						destination.setBorder(BLUE_BORDER);
						markSquareClickable(destination);
					}
				} else {
					throw new RuntimeException("Cannot select a piece from opponent to start a move");
				}
			} else {
				throw new RuntimeException("Cannot select an empty square to start a move");
			}
		}
	}

	private void info(String text, boolean withPopup) {
		logger.info("[INFO] {}", text);
		if (withPopup) {
			view.popupInfo(text);
		}
	}

	private void error(Exception exception, boolean withPopup) {
		logger.error("An error happened: {}", exception.getMessage(), exception);
		if (withPopup) {
			view.popupError(exception.getMessage());
		}
	}

	private boolean isGameOver(Game game) {
		return !moveService.getGameState(game.getBoard(), game.getToPlay(), game.getHistory()).isInProgress();
	}

	private void cleanSelectedSquare() {
		this.selectedSquare = null;
	}

	void saveGame() {
		Optional<File> file = view.saveGameDialog();
		if (file.isPresent()) {
			try {
				fileService.writeGameToFileBasicNotation(game, file.get());
			} catch (IOException e) {
				error(e, true);
			}
		}
	}

	// TODO: Implement
	void openGame() {
		// Getting the moves
		List<Map<String, String>> data = Database.getPlayers();
		String[] choices = new String[data.size()];
		for (int i = 0; i < data.size(); i++)
		{
			Map<String, String> map = data.get(i);
			String item = "(" + map.get("id") + ") " + map.get("white_player") + " [W] vs. " + map.get("black_player") +" [B]";
			choices[i] = item;
		}

		JLabel messageLabel = new JLabel("Select a game to review: ");
		final JComboBox<String> comboBox = new JComboBox<>(choices);
		final JComponent[] inputs = new JComponent[]{messageLabel, comboBox};
		JOptionPane.showConfirmDialog(null, inputs, "Open Game", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE);

		// Load data from the database
		String selectedItem = (String) comboBox.getSelectedItem();
		int selectedID = Integer.valueOf(selectedItem.substring(1, selectedItem.indexOf(')')));
		List<Map<String, String>> moveData = Database.getMovesFromID(selectedID);
		List<String> moves = new LinkedList<>();
		for (Map<String, String> map : moveData)
			moves.add(map.get("move"));
		applyMovesFromBasicNotationFile(moves);
		play();
	}

	void loadGame() {
		Optional<File> fileOpt = view.loadGameDialog();
		if (fileOpt.isPresent()) {
			try {
				// we assume that the game to load is played in the same config as currently
				Game loadedGame = new Game(game.getWhitePlayer(), game.getBlackPlayer());
				this.game = loadedGame;
				this.board = loadedGame.getBoard();
				refreshBoardView(board);

				File file = fileOpt.get();
				List<String> lines = fileService.readFile(Paths.get(file.toURI()));
				if (file.getName().endsWith(".pgn")) {
					applyMovesFromPortableGameNotationFile(lines);
				} else {
					applyMovesFromBasicNotationFile(lines);
				}
				play();
			} catch (IOException e) {
				error(e, true);
			}
		}
	}
	void applyMovesFromPortableGameNotationFile(List<String> lines) {
		Game loadedGame = portableGameNotationService.readPgnFile(lines);
		loadedGame.getHistory().forEach(this::doMove);
	}

	void applyMovesFromBasicNotationFile(List<String> lines) {
		for (String line: lines) {
			Move move = Move.fromBasicNotation(line, game.getToPlay());
			Piece piece = board.getPiece(move.getFromX(), move.getFromY())
					.orElseThrow(() -> new RuntimeException("Unexpected move, no piece at this location"));
			doMove(new Move(piece, move.getFromX(), move.getFromY(), move.getToX(), move.getToY()));
		}
	}

	void printGameToConsole() {
		logger.debug("Current board: \n{}", board.toString());
	}

	void suggestMove() {
		Instant start = Instant.now();
		Move move = botToSuggestMove.selectMove(game);
		Instant end = Instant.now();
		if (showTiming) {
			logger.debug("Time to suggest move: {}", Duration.between(start, end));
		}
		info("Brilliantly, I recommend you to play: " + move.toString(), true);
	}

	void undoLastMove() {
		if (game.getHistory().size() < 2) {
			return;
		}
		Move lastMove = getLastMove();
		undoLastMove(lastMove);
		Move secondLastMove = getLastMove();
		undoLastMove(secondLastMove);
		play();
	}

	void requestDraw()
	{
		Player playerPlaying = game.getPlayerToPlay();
		Player playerWaiting = game.getPlayerWaiting();
		if (playerPlaying.isBot() && !playerWaiting.isBot())
			info("Sorry, it is not your turn", true);
		else if (!playerPlaying.isBot() && playerWaiting.isBot())
			this.evaluateDrawProposal();
		else
			this.proposeDraw();
	}

	void evaluateDrawProposal() {
		Player playerWaiting = game.getPlayerWaiting();
		boolean drawAccepted = ((Bot) playerWaiting).isDrawAcceptable(game);
		if (drawAccepted) {
			info("Hmmm OK, I hate draws but you played quite well... Accepted!", true);
			game.setState(GameState.DRAW_AGREEMENT);
			cleanSelectedSquare();
			view.cleanSquaresBorder();
			view.resetAllClickables();
			displayGameInfo(null);
		} else {
			info("Are you kidding me? A champion like me can't accept such proposal (at least not now).", true);
		}
	}

	void proposeDraw() {
		Player playerPlaying = game.getPlayerToPlay();
		Player playerWaiting = game.getPlayerWaiting();

		JRadioButton acceptDraw = new JRadioButton("Accept draw", false);
		JRadioButton rejectDraw = new JRadioButton("Reject draw", true);
		ButtonGroup opponentButtonGroup = new ButtonGroup();
		opponentButtonGroup.add(acceptDraw);
		opponentButtonGroup.add(rejectDraw);

		JLabel messageLabel = new JLabel(playerPlaying.getName() + " has proposed a draw. " + playerWaiting.getName() + ", will you accept a draw?");

		final JComponent[] inputs = new JComponent[]{messageLabel, acceptDraw, rejectDraw};
		JOptionPane.showConfirmDialog(null, inputs, "Draw?", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE);

		if (acceptDraw.isSelected())
		{
			info(playerWaiting.getName() + " has accepted the draw.", true);
			game.setState(GameState.DRAW_AGREEMENT);
			cleanSelectedSquare();
			view.cleanSquaresBorder();
			view.resetAllClickables();
			displayGameInfo(null);
		}
		else
			info(playerWaiting.getName() + " has rejected the draw! The game continues!", true);
	}
	private Move getLastMove() {
		return game.getHistory().get(game.getHistory().size() - 1);
	}

	private static class FindBestMoveTask extends SwingWorker<Move, Object> {
		final private Bot bot;
		final private Game game;

		public FindBestMoveTask(Bot bot, Game game) {
			this.bot = bot;
			this.game = game;
		}

		@Override
		protected Move doInBackground() {
			return bot.selectMove(game);
		}
	}
}
