package entities;

import java.util.ArrayList;

public class Server {
    private final String name;
    private final String grpc_address, rest_address;
    private ArrayList<String> shards;
    private long heartbeat_time = 0;
    private static long current_time = 0;


    public Server(String name) {
        this(name, "", "");
    }

    public Server(String name, String grpc_address, String rest_address) {
        this.name = name;
        this.grpc_address = grpc_address;
        this.rest_address = rest_address;
        heartbeat();
    }

    public ArrayList<String> getShards() {
        return shards;
    }

    public void setShards(ArrayList<String> shards) {
        this.shards = shards;
    }

    @Override
    public String toString() {
        return "Server{" +
                "name='" + name + '\'' +
                ", grpc_address='" + grpc_address + '\'' +
                ", rest_address='" + rest_address + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public String getGrpcAddress() {
        return grpc_address;
    }

    public String getGrpcPort() {
        return grpc_address.split(":")[1];
    }

    public String getRestPort() {
        return rest_address.split(":")[1];
    }

    public String getRestAddress() {
        return rest_address.split(":")[1];
    }

    public boolean isAlive() {
        return heartbeat_time == current_time;
    }

    public void heartbeat() {
        heartbeat_time = current_time;
    }

    public static void killAll() {
        current_time++;
    }
}