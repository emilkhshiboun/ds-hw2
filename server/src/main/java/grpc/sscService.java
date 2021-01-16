package grpc;

import entities.Reservation;
import entities.Ride;
import generated.*;
import io.grpc.stub.StreamObserver;
import management.ServerManager;

public class sscService extends sscGrpc.sscImplBase {

    private final int port;

    public sscService(int port) {
        this.port = port;
    }

    @Override
    public void upsert(ride request, StreamObserver<response> responseObserver) {
        System.out.println("Server port [" + port + "]");
        responseObserver.onNext(response.newBuilder().setMsg("YEs").build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRidesAsync(emptyMessage request, StreamObserver<response> responseObserver) {
        ServerManager.getInstance().getRides().stream().map(ride ->
                response.newBuilder().setMsg(ride.serialize()).build())
                .forEach(responseObserver::onNext);
        responseObserver.onCompleted();

    }

    //    @Override
    public void getRides(generated.emptyMessage request, StreamObserver<generated.response> responseObserver) {
        ServerManager.getInstance().getRides().stream().filter((element) -> {
            ServerManager sm = ServerManager.getInstance();
            return sm.getLeaderShards().contains(sm.getCityShard(element.getStartPosition().getName()));
        }).forEach((ride) -> {
            responseObserver.onNext(response.newBuilder().setMsg(ride.toString()).build());
        });
        responseObserver.onCompleted();
    }

    @Override
    public void addRideLeader(ride request, StreamObserver<response> responseObserver) {
        ServerManager sm = ServerManager.getInstance();
        Ride ride = new Ride(request);
        String result = sm.addRideBroadCast(ride).serialize();
        responseObserver.onNext(response.newBuilder().setMsg(result).build());
        responseObserver.onCompleted();
    }

    /**
     * A follower will execute this if he received command from leader to save a given ride.
     */
    @Override
    public void addRideFollower(ride request, StreamObserver<response> responseObserver) {
        ServerManager sm = ServerManager.getInstance();
        Ride ride = new Ride(request);
        sm.addRide(ride);
        responseObserver.onNext(response.newBuilder().build());
        responseObserver.onCompleted();
    }


    @Override
    public void reserveRideLeader(reservation request, StreamObserver<ride> responseObserver) {
        ServerManager sm = ServerManager.getInstance();
        Reservation reservation = new Reservation(request);
        Ride ride = sm.addReservation(reservation);
//        responseObserver.onNext(get);
        responseObserver.onCompleted();
    }
}