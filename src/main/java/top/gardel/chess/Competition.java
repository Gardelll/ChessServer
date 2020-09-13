package top.gardel.chess;

import com.google.protobuf.Any;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import top.gardel.chess.proto.AuthInfo;
import top.gardel.chess.proto.CompetitionOperation;
import top.gardel.chess.proto.Response;

@EqualsAndHashCode
@Getter
@Setter
public class Competition {
    private final int id;
    private final byte[][] chessPlate;
    private final Player playerA;
    private Player playerB;
    private Player lastPut;
    private int playerAWin;
    private int playerBWin;
    private int playerALose;
    private int playerBLose;

    public Competition(int id, Player playerA) {
        this.id = id;
        this.playerA = playerA;
        chessPlate = new byte[3][3];
        playerB = null;
        playerAWin = playerBWin = playerALose = playerBLose = 0;
    }

    public void setPlayerB(Player playerB) {
        if (playerB != null) {
            playerA.getChannel().writeAndFlush(Response.newBuilder()
                .setBody(Any.pack(CompetitionOperation.newBuilder()
                    .setId(getId())
                    .setOperation(CompetitionOperation.Operation.Join)
                    .setPlayerB(AuthInfo.newBuilder().setUuid(playerB.getUuid().toString()).build())
                    .build())));
        } else {
            playerA.getChannel().writeAndFlush(Response.newBuilder()
                .setBody(Any.pack(CompetitionOperation.newBuilder()
                    .setId(getId())
                    .setOperation(CompetitionOperation.Operation.Leave)
                    .setPlayerB(AuthInfo.newBuilder().setUuid(this.playerB == null ? "B" : this.playerB.getUuid().toString()).build())
                    .build())));
        }
        this.playerB = playerB;
    }

    public int getSize() {
        return chessPlate.length;
    }

    /**
     * 获取棋子
     * @param x [1, 3]
     * @param y [1, 3]
     * @return 0, 'A', 'B'
     */
    public byte getChessAt(int x, int y) {
        checkPoint(x, y);
        return chessPlate[x - 1][y - 1];
    }

    public boolean hasChess(int x, int y) {
        return getChessAt(x, y) != 0;
    }

    public boolean putChess(Player player, int x, int y) {
        if (hasChess(x, y)) return false;
        Objects.requireNonNull(player);
        if (!hasPlayerB()) return false;
        if (player.equals(lastPut)) return false;
        if (checkWinner() != 0) return false;
        if (player.equals(playerA)) chessPlate[x - 1][y - 1] = 'A';
        else if (player.equals(playerB)) chessPlate[x - 1][y - 1] = 'B';
        else return false;
        lastPut = player;
        playerA.sendPutChess(lastPut.equals(playerA), x, y);
        playerB.sendPutChess(lastPut.equals(playerB), x, y);
        return true;
    }

    public void reset() {
        byte winner = checkWinner();
        for (int i = 0; i < chessPlate.length; i++) {
            for (int j = 0; j < chessPlate[0].length; j++) {
                chessPlate[i][j] = 0;
            }
        }
        switch (winner) {
            case 'A':
                playerAWin++;
                playerBLose++;
                lastPut = playerB;
                break;
            case 'B':
                playerALose++;
                playerBWin++;
                lastPut = playerA;
                break;
        }
        if (playerA != null) {
            playerA.sendOperationResponse(CompetitionOperation.Operation.Reset);
        }
        if (playerB != null) {
            playerB.sendOperationResponse(CompetitionOperation.Operation.Reset);
        }
    }

    public boolean isFull() {
        for (byte[] bytes : chessPlate) {
            for (int j = 0; j < chessPlate[0].length; j++) {
                if (bytes[j] == 0) return false;
            }
        }
        return true;
    }

    public byte checkWinner() {
        if (!hasPlayerB()) return 0;
        for (byte[] bytes : chessPlate) {
            byte lastChess = bytes[0];
            byte connected = 1;
            for (int j = 1; j < bytes.length; j++) {
                if (lastChess != 0) {
                    if (bytes[j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = bytes[j];
            }
            if (connected == 3 && lastChess != 0) return lastChess;
        }
        for (int j = 0; j < chessPlate[0].length; j++) {
            byte lastChess = chessPlate[0][j];
            byte connected = 1;
            for (int i = 1; i < chessPlate.length; i++) {
                if (lastChess != 0)  {
                    if (chessPlate[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = chessPlate[i][j];
            }
            if (connected == 3 && lastChess != 0) return lastChess;
        }
        {
            byte lastChess = chessPlate[0][0];
            byte connected = 1;
            for (int i = 1, j = 1; i < chessPlate.length && j < chessPlate[0].length; j = ++i) {
                if (lastChess != 0)  {
                    if (chessPlate[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = chessPlate[i][j];
            }
            if (connected == 3 && lastChess != 0) return lastChess;
        }
        {
            byte lastChess = chessPlate[0][2];
            byte connected = 1;
            for (int i = 1, j = 1; i < chessPlate.length && j >= 0; j--, i++) {
                if (lastChess != 0)  {
                    if (chessPlate[i][j] == lastChess) connected++;
                    else connected = 1;
                }
                lastChess = chessPlate[i][j];
            }
            if (connected == 3 && lastChess != 0) return lastChess;
        }
        if (isFull()) return 'N';
        return 0;
    }

    public boolean hasPlayerB() {
        return playerB != null;
    }

    private void checkPoint(int x, int y) {
        if (x < 1 || x > 3 || y < 1 || y > 3)
            throw new IllegalArgumentException(String.format("x = %d, y = %d", x, y));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("-".repeat(chessPlate[0].length * 2 + 1))
            .append('\n');
        for (byte[] bytes : chessPlate) {
            sb.append('|');
            for (byte aByte : bytes) {
                switch (aByte) {
                    case 'A':
                        sb.append("O|");
                        break;
                    case 'B':
                        sb.append("X|");
                        break;
                    default:
                        sb.append(" |");
                        break;
                }
            }
            sb.append('\n');
            sb.append("-".repeat(bytes.length * 2 + 1));
            sb.append('\n');
        }
        return sb.toString();
    }
}
