package ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import modelo.*;
import logica.*;
import estructuras.*;
import excepciones.*;

public class MainApp extends Application {
    private GameContext ctx;
    private GridPane gameGrid;
    private Label lblVida, lblAtaque, lblDefensa, lblVelocidad, lblTurnos, lblHabitacion;
    private TextArea logArea;
    private Button btnAtacar, btnRecoger, btnUsar, btnAbrirPuerta, btnCaminoOptimo;

    public static class GameContext {
        public Jugador jugador;
        public Grafo grafo;
        public Habitacion[] habitaciones;
        public Habitacion currentHabitacion;
        public GestorTurnos gestorTurnos;
        public InteraccionService interaccionService;
        public int exitHabitacionId;

        public GameContext() {
            this.jugador = new Jugador(100, 20, 10, 1, 0, 0);
            this.jugador.setHabitacionId(0);
            
            int numRooms = 5;
            this.grafo = new Grafo(numRooms);
            this.grafo.addEdge(0, 1, true);
            this.grafo.addEdge(1, 2, true);
            this.grafo.addEdge(2, 3, true);
            this.grafo.addEdge(3, 4, true);
            this.exitHabitacionId = 4;

            this.habitaciones = new Habitacion[numRooms];
            for (int i = 0; i < numRooms; i++) {
                this.habitaciones[i] = new Habitacion(5, 5);
                populateRoom(this.habitaciones[i], i);
            }
            
            this.currentHabitacion = habitaciones[this.jugador.getHabitacionId()];
            this.gestorTurnos = new GestorTurnos(50, jugador);
            this.interaccionService = new InteraccionService();
        }

        private void populateRoom(Habitacion h, int roomId) {
            // Add some enemies
            h.getCelda(1, 1).setEntidad(new Enemigo(60, 10, 5, 1, 1));
            h.getCelda(3, 2).setEntidad(new Enemigo(80, 12, 2, 3, 2));
            
            // Add some objects
            h.getCelda(0, 2).setObjeto(new Consumible("Poción", "Cura vida", 20));
            h.getCelda(2, 0).setObjeto(new Equipable("Escudo", "Más defensa", 0, 10));
            
            // Add doors to next rooms
            if (roomId < 4) {
                h.getCelda(4, 4).setObjeto(new Puerta("Puerta " + roomId, "Lleva a la siguiente sala", "door" + roomId, roomId + 1));
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        ctx = new GameContext();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Center: Game Grid
        gameGrid = new GridPane();
        gameGrid.setAlignment(Pos.CENTER);
        gameGrid.setHgap(5);
        gameGrid.setVgap(5);
        root.setCenter(gameGrid);

        // Right: Player and Actions
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(0, 0, 0, 10));
        
        VBox playerPanel = new VBox(5);
        playerPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 10;");
        lblVida = new Label();
        lblAtaque = new Label();
        lblDefensa = new Label();
        lblVelocidad = new Label();
        lblTurnos = new Label();
        lblHabitacion = new Label();
        playerPanel.getChildren().addAll(
            new Label("--- ESTADÍSTICAS ---"),
            lblVida, lblAtaque, lblDefensa, lblVelocidad, lblTurnos, lblHabitacion
        );

        VBox actionPanel = new VBox(10);
        actionPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1; -fx-padding: 10;");
        btnAtacar = new Button("Atacar");
        btnRecoger = new Button("Recoger");
        btnUsar = new Button("Usar Objeto");
        btnAbrirPuerta = new Button("Abrir Puerta");
        btnCaminoOptimo = new Button("Mostrar camino óptimo");
        
        actionPanel.getChildren().addAll(
            new Label("--- ACCIONES ---"),
            btnAtacar, btnRecoger, btnUsar, btnAbrirPuerta, btnCaminoOptimo
        );

        rightPanel.getChildren().addAll(playerPanel, actionPanel);
        root.setRight(rightPanel);

        // Bottom: Log
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        root.setBottom(logArea);

        // Event Handlers
        btnAtacar.setOnAction(e -> handleAtacar());
        btnRecoger.setOnAction(e -> handleRecoger());
        btnUsar.setOnAction(e -> handleUsar());
        btnAbrirPuerta.setOnAction(e -> handleAbrirPuerta());
        btnCaminoOptimo.setOnAction(e -> handleCaminoOptimo());

        updateUI();

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setTitle("Dungeon Crawler - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void updateUI() {
        // Update current room reference
        ctx.currentHabitacion = ctx.habitaciones[ctx.jugador.getHabitacionId()];

        // Update stats
        lblVida.setText("Vida: " + ctx.jugador.getVida());
        lblAtaque.setText("Ataque: " + ctx.jugador.getAtaqueTotal());
        lblDefensa.setText("Defensa: " + ctx.jugador.getDefensaTotal());
        lblVelocidad.setText("Velocidad: " + ctx.jugador.getVelocidad());
        lblTurnos.setText("Turnos restantes: " + ctx.gestorTurnos.getTurnosRestantes());
        lblHabitacion.setText("Habitación: " + ctx.jugador.getHabitacionId());

        // Update grid
        gameGrid.getChildren().clear();
        int rows = ctx.currentHabitacion.getFilas();
        int cols = ctx.currentHabitacion.getColumnas();
        
        ListaEnlazada<Posicion> alcanzables = MovimientoService.obtenerCeldasAlcanzables(ctx.jugador, ctx.currentHabitacion);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                StackPane cell = new StackPane();
                cell.setPrefSize(40, 40);
                cell.setStyle("-fx-border-color: lightgray;");

                Celda logicCell = ctx.currentHabitacion.getCelda(r, c);
                
                // Highlight if reachable
                boolean isReachable = false;
                for (int i = 0; i < alcanzables.size(); i++) {
                    Posicion p = alcanzables.get(i);
                    if (p.fila == r && p.columna == c) {
                        isReachable = true;
                        break;
                    }
                }

                if (isReachable) {
                    cell.setBackground(new Background(new BackgroundFill(Color.YELLOW, CornerRadii.EMPTY, Insets.EMPTY)));
                } else {
                    cell.setBackground(new Background(new BackgroundFill(Color.LIGHTGRAY, CornerRadii.EMPTY, Insets.EMPTY)));
                }

                // Entity Representation
                if (r == ctx.jugador.getFila() && c == ctx.jugador.getColumna()) {
                    Label label = new Label("P");
                    label.setTextFill(Color.BLUE);
                    cell.getChildren().add(label);
                } else if (logicCell.hasEntidad()) {
                    Label label = new Label("E");
                    label.setTextFill(Color.RED);
                    cell.getChildren().add(label);
                } else if (logicCell.hasObjeto()) {
                    Objeto obj = logicCell.getObjeto();
                    String text = (obj instanceof Puerta) ? "D" : "O";
                    Label label = new Label(text);
                    cell.getChildren().add(label);
                }

                // Movement Click
                if (isReachable) {
                    final int finalR = r;
                    final int finalC = c;
                    cell.setOnMouseClicked(e -> {
                        try {
                            MovimientoService.moverJugador(ctx.jugador, ctx.currentHabitacion, new Posicion(finalR, finalC));
                            log("Te has movido a (" + finalR + ", " + finalC + ")");
                            ctx.gestorTurnos.finalizarTurno();
                        } catch (MovimientoInvalidoException ex) {
                            log("Error: " + ex.getMessage());
                        }
                        updateUI();
                    });
                }

                gameGrid.add(cell, c, r);
            }
        }
    }

    private void log(String msg) {
        logArea.appendText(msg + "\n");
    }

    private void handleAtacar() {
        // Implementation based on InteraccionService
        // For now, find adjacent enemy
        log("Intentando atacar...");
        // Logic to find enemy and call ctx.interaccionService.atacarEnemigo
        updateUI();
    }

    private void handleRecoger() {
        try {
            Celda current = ctx.currentHabitacion.getCelda(ctx.jugador.getFila(), ctx.jugador.getColumna());
            ctx.interaccionService.recogerObjeto(ctx.jugador, current);
            log("Objeto recogido correctamente.");
        } catch (AccionInvalidaException ex) {
            log("Error: " + ex.getMessage());
        }
        updateUI();
    }

    private void handleUsar() {
        log("Abriendo menú de inventario...");
        // Logic to select and use object
        updateUI();
    }

    private void handleAbrirPuerta() {
        try {
            Celda current = ctx.currentHabitacion.getCelda(ctx.jugador.getFila(), ctx.jugador.getColumna());
            Objeto obj = current.getObjeto();
            if (obj instanceof Puerta) {
                ctx.interaccionService.abrirPuerta(ctx.jugador, (Puerta) obj, ctx.gestorTurnos);
                log("Puerta abierta correctamente.");
            } else {
                log("No hay ninguna puerta aquí.");
            }
        } catch (AccionInvalidaException ex) {
            log("Error: " + ex.getMessage());
        }
        updateUI();
    }

    private void handleCaminoOptimo() {
        ListaEnlazada<Integer> path = BFS.findShortestPath(ctx.grafo, ctx.jugador.getHabitacionId(), ctx.exitHabitacionId);
        if (path != null) {
            int distance = path.size() - 1;
            log("Camino óptimo encontrado. Distancia: " + distance + " habitaciones.");
            log("Habitaciones restantes para llegar: " + distance);
        } else {
            log("No se encontró camino al objetivo.");
        }
    }
}
