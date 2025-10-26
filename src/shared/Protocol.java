package shared;

/**
 * Protocol constants cho giao tiếp Client-Server
 */
public class Protocol {
    // Client -> Server
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String CREATE_ROOM = "CREATE_ROOM";
    public static final String JOIN_ROOM = "JOIN_ROOM";
    public static final String LEAVE_ROOM = "LEAVE_ROOM";
    public static final String INVITE = "INVITE";
    public static final String INVITE_RESPONSE = "INVITE_RESPONSE";
    public static final String KICK = "KICK";
    public static final String READY = "READY";
    public static final String START_GAME = "START_GAME";
    public static final String SCORE_UPDATE = "SCORE_UPDATE";
    public static final String FINISH = "FINISH";
    public static final String MAX_SCORE = "MAX_SCORE";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CHAT = "CHAT";
    public static final String GET_LEADERBOARD = "GET_LEADERBOARD";
    public static final String GET_HISTORY = "GET_HISTORY";
    public static final String GET_PROFILE = "GET_PROFILE";
    public static final String UPDATE_PROFILE = "UPDATE_PROFILE";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String FORGOT_PASSWORD = "FORGOT_PASSWORD";
    public static final String GET_ONLINE_USERS = "GET_ONLINE_USERS";
    public static final String REQUEST_JOIN_ROOM = "REQUEST_JOIN_ROOM";
    public static final String JOIN_REQUEST_RESPONSE = "JOIN_REQUEST_RESPONSE";
    public static final String FIND_MATCH = "FIND_MATCH";
    public static final String CANCEL_FIND_MATCH = "CANCEL_FIND_MATCH";
    public static final String MATCH_FOUND = "MATCH_FOUND";
    public static final String GET_ALL_USERS = "GET_ALL_USERS";
    
    // Server -> Client
    public static final String REGISTER_RESPONSE = "REGISTER_RESPONSE";
    public static final String LOGIN_RESPONSE = "LOGIN_RESPONSE";
    public static final String ONLINE_USERS_UPDATE = "ONLINE_USERS_UPDATE";
    public static final String ROOM_CREATED = "ROOM_CREATED";
    public static final String ROOM_JOINED = "ROOM_JOINED";
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String INVITATION = "INVITATION";
    public static final String INVITE_ACCEPTED = "INVITE_ACCEPTED";
    public static final String INVITE_DECLINED = "INVITE_DECLINED";
    public static final String INVITE_EXPIRED = "INVITE_EXPIRED";
    public static final String PLAYER_KICKED = "PLAYER_KICKED";
    public static final String PLAYER_READY = "PLAYER_READY";
    public static final String GAME_START = "GAME_START";
    public static final String OPPONENT_SCORE = "OPPONENT_SCORE";
    public static final String GAME_END = "GAME_END";
    public static final String OPPONENT_LEFT = "OPPONENT_LEFT";
    public static final String OPPONENT_FINISHED = "OPPONENT_FINISHED";
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String LEADERBOARD_DATA = "LEADERBOARD_DATA";
    public static final String HISTORY_DATA = "HISTORY_DATA";
    public static final String PROFILE_DATA = "PROFILE_DATA";
    public static final String UPDATE_SUCCESS = "UPDATE_SUCCESS";
    public static final String ERROR = "ERROR";
    public static final String JOIN_REQUEST_NOTIFICATION = "JOIN_REQUEST_NOTIFICATION";
    public static final String JOIN_REQUEST_RESULT = "JOIN_REQUEST_RESULT";
    
    // Error codes
    public static final int ERR_USERNAME_EXISTS = 1001;
    public static final int ERR_INVALID_CREDENTIALS = 1002;
    public static final int ERR_SESSION_EXPIRED = 1003;
    public static final int ERR_ALREADY_LOGGED_IN = 1004;
    public static final int ERR_ROOM_NOT_FOUND = 2001;
    public static final int ERR_ROOM_FULL = 2002;
    public static final int ERR_GAME_STARTED = 2003;
    public static final int ERR_NOT_HOST = 2004;
    public static final int ERR_CANNOT_KICK_SELF = 2005;
    public static final int ERR_NOT_READY = 3001;
    public static final int ERR_INVALID_GRAIN = 3002;
    public static final int ERR_TIME_UP = 3003;
    public static final int ERR_GAME_NOT_STARTED = 3004;
    public static final int ERR_CONNECTION_LOST = 4001;
    public static final int ERR_TIMEOUT = 4002;
    public static final int ERR_INVALID_PACKET = 4003;
    public static final int ERR_ROOM_COOLDOWN = 5001;
    public static final int ERR_JOIN_REQUEST_DENIED = 5002;
}

