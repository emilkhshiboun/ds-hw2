package management;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import entities.City;
import entities.Reservation;
import entities.Ride;
import entities.Server;
import grpc.GrpcMain;
import grpc.sscClient;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.LoggerFactory;
import rest.host.RestMain;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ServerManager {


    private Logger logger;

    public ArrayList<String> getLeaderShards() {
        return primary_shards;
    }

    /**
     * This server name.
     */
    private Server current_server;
    /**
     * Saves All cities in the system.
     */
    private Set<City> cities;
    /**
     * Maps shard name to its server leader name
     */
    private Map<String, Server> system_shards;

    /**
     * Contains all servers in the system (dead and alive!)
     */
    private Map<String, Server> system_servers;


    /**
     * Represents the name of the shards that this server is leader for.
     */
    private ArrayList<String> primary_shards;

    /**
     * Represents all the rides from cities which this leader is responsible for (@field shards)
     */
    private final ArrayList<Ride> rides;

    private static ServerManager instance = null;

    public ZKManager zk;


    private ServerManager() {
        cities = new HashSet<>();
        rides = new ArrayList<>();
        system_shards = new HashMap<>();
        primary_shards = new ArrayList<>();
        system_servers = new HashMap<>();
    }


    public static ServerManager getInstance() {
        if (instance == null) {
            instance = new ServerManager();
            instance.logger = Logger.getLogger(ServerManager.class.getName());
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public boolean init(String file_path, String server_name) {
        current_server = new Server(server_name);
        Map<String, Object> json_data = getJsonMap(file_path);
        if (json_data == null) {
            return false;
        }
        String zk_addr = (String) json_data.get("zk-address");
        zk = new ZKManager(zk_addr);

        // initializing cities array
        JSONArray city_array = (JSONArray) json_data.get("cities");
        city_array.forEach(obj -> {
            JSONObject city_info = (JSONObject) obj;
            String city_name = city_info.get("city-name").toString();
            double x = Double.parseDouble(city_info.get("X").toString());
            double y = Double.parseDouble(city_info.get("Y").toString());
            String shard = city_info.get("shard").toString();
            City city = new City(city_name, x, y, shard);
            cities.add(city);
        });

        // initializing servers array
        Set<String> system_shards_aux = new HashSet<>();
        JSONArray servers_array = (JSONArray) json_data.get("servers");
        servers_array.forEach(obj -> {
            JSONObject server_object = (JSONObject) obj;
            String serverName = server_object.get("server-name").toString();
            String grpc_address = server_object.get("grpc-address").toString();
            String rest_address = server_object.get("rest-address").toString();
            JSONArray server_shards = (JSONArray) server_object.get("shards");
            Server server = new Server(serverName, grpc_address, rest_address);
            ArrayList<String> shards = new ArrayList<String>(server_shards);
            system_shards_aux.addAll(shards);
            server.setShards(shards);
            system_servers.put(serverName, server);
            if (serverName.equals(server_name)) {
                this.current_server = server;
            }

        });
        system_shards_aux.forEach(shard -> system_shards.put(shard, null));
        return true;
    }

    public boolean start() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        if (!zk.connect()) return false;
        GrpcMain.run("8000");
        RestMain.run("8080");
        return true;
    }


    /**
     * given a city name, return the current leader server for this city.
     */
    public Server getLeader(String start_city) {
        City needed_city = cities.stream().filter(city -> city.getName().equals(start_city))
                .findAny().orElse(null);

        assert needed_city != null;
        return system_shards.get(needed_city.getShard());
    }

    public Set<Server> getCityFollowers(City needed_city) {
        Server leader_server = system_shards.get(needed_city.getShard());
        String city_shard = needed_city.getShard();
        return system_servers.values().stream()
                .filter(server -> !server.getName().equals(leader_server.getName()) && server.getShards().contains(city_shard))
                .collect(Collectors.toSet());

    }

    public Ride addRideBroadCast(Ride ride) {
        assert ride.getVacancies() > 0;
        assert getServer().getName().equals(getLeader(ride.getStartPosition().getName()).getName());
        Ride result_ride = addRide(ride);
        if (result_ride.getId() < 0) {
            result_ride.setId(zk.generateUniqueId());
        }
        Set<Server> followers = ServerManager.getInstance().getCityFollowers(result_ride.getStartPosition());
        zk.atomicBroadCastMessage(followers, MessageFactory.newRideBroadCastMessage(result_ride));
        return result_ride; // response to the user.
    }


    /**
     * add a given ride to the rides array only if there doesn't exist another ride with the same parameters.
     * Important: We want this to be idempotent, in case there was a leader change and some followers executed it while
     * others didn't (this happens if the leader fails while broadcasting). To prevent this we retry with several leaders, and thus,
     * a new leader will broadcast again, and a follower might receive a command to add the same ride several times.
     */
    public Ride addRide(Ride ride) {
        synchronized (this.rides) {
            Ride existing_ride = this.rides.stream().filter(ride::equals).findAny().orElse(null);
            if (existing_ride == null) {
                rides.add(ride);
                return ride;
            }
            return existing_ride;
        }
    }

    public Ride addReservation(Reservation reservation) {

        // TODO: check if there is a suitable ride, if so reserve it and return ride, otherwise return "No ride was found"
        return null;
    }

    public String getSnapshot() {
        CountDownLatch latch = new CountDownLatch(system_shards.size());
        ArrayList<String> rides = new ArrayList<>();
        for (Server server : system_shards.values()) {
            sscClient grpc_client = new sscClient(server.getGrpcAddress());
            grpc_client.getRides(rides, latch);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Error getting snapshot");
            e.printStackTrace();
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        rides.forEach(stringBuilder::append);
        return stringBuilder.toString();
    }

    public ArrayList<Ride> getRides() {
        return rides;
    }


    public String getCityShard(String city_name) {
        City city = cities.stream()
                .filter(elem -> elem.getName().equals(city_name))
                .findAny()
                .orElse(null);
        assert city != null;
        return city.getShard();
    }

    public Server getServer() {
        return current_server;
    }

    public Collection<Server> getAliveServers() {
        return system_servers.values().stream().filter(Server::isAlive).collect(Collectors.toList());
    }

    public Map<String, Server> getSystemShards() {
        return system_shards;
    }


    public synchronized void updateShardLeader(String shard, Server server) {
        logger.log(Level.INFO, "new shard leader" + shard + " server=" + server.getName());
        system_shards.put(shard, server);
    }

    public void updateAliveServers(List<String> alive) {
        Server.killAll();
        alive.forEach(server_name -> system_servers.get(server_name).heartbeat());
    }

    public City getCity(String city_name) {
        return cities.stream().filter(city -> city.getName().equals(city_name)).findAny().orElse(null);
    }

    private Map<String, Object> getJsonMap(String file_path) {
        JSONParser jsonParser = new JSONParser();
        Map<String, Object> json_data = new HashMap<String, Object>();
        try (FileReader reader = new FileReader(file_path)) {
            JSONObject main_object = (JSONObject) jsonParser.parse(reader);
            @SuppressWarnings("unchecked")
            Set<Map.Entry<String, Object>> set = main_object.entrySet();
            for (Map.Entry<String, Object> entry : set) {
                json_data.put(entry.getKey(), entry.getValue());
            }
        } catch (IOException | ParseException e) {
            logger.log(Level.SEVERE, "Could not config server: " + current_server.getName());
            e.printStackTrace();
            return null;
        }
        return json_data;
    }


    public static class MessageFactory {
        public static final String OPERATION = "operation";
        public static final String DATA = "data";

        public static final String OPERATION_NEW_RIDE = "new-ride";

        private static String newRideBroadCastMessage(Ride new_ride) {
            JsonObject json_message = new JsonObject();
            json_message.add(OPERATION, new JsonPrimitive(OPERATION_NEW_RIDE));
            json_message.add(DATA, JsonParser.parseString(new_ride.serialize()));
            return json_message.toString();
        }

        public static void ProcessMessage(byte[] message) {
            String data_string = new String(message, StandardCharsets.UTF_8);
            JsonObject json_message = JsonParser.parseString(data_string).getAsJsonObject();
            JsonElement operation = json_message.get(OPERATION);
            if (operation != null) {
                if (operation.getAsString().equals(OPERATION_NEW_RIDE)) {
                    JsonObject ride_as_json = json_message.get(DATA).getAsJsonObject();
                    Ride ride_to_add = Ride.deserialize(ride_as_json);
                    ServerManager.getInstance().addRide(ride_to_add);
                }
            }
        }

    }
}
