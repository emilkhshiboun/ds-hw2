// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: scheme.proto

package generated;

public interface reservationOrBuilder extends
    // @@protoc_insertion_point(interface_extends:ssc.reservation)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string first_name = 1;</code>
   * @return The firstName.
   */
  java.lang.String getFirstName();
  /**
   * <code>string first_name = 1;</code>
   * @return The bytes for firstName.
   */
  com.google.protobuf.ByteString
      getFirstNameBytes();

  /**
   * <code>string last_name = 2;</code>
   * @return The lastName.
   */
  java.lang.String getLastName();
  /**
   * <code>string last_name = 2;</code>
   * @return The bytes for lastName.
   */
  com.google.protobuf.ByteString
      getLastNameBytes();

  /**
   * <code>string departure_time = 3;</code>
   * @return The departureTime.
   */
  java.lang.String getDepartureTime();
  /**
   * <code>string departure_time = 3;</code>
   * @return The bytes for departureTime.
   */
  com.google.protobuf.ByteString
      getDepartureTimeBytes();

  /**
   * <code>repeated string path = 4;</code>
   * @return A list containing the path.
   */
  java.util.List<java.lang.String>
      getPathList();
  /**
   * <code>repeated string path = 4;</code>
   * @return The count of path.
   */
  int getPathCount();
  /**
   * <code>repeated string path = 4;</code>
   * @param index The index of the element to return.
   * @return The path at the given index.
   */
  java.lang.String getPath(int index);
  /**
   * <code>repeated string path = 4;</code>
   * @param index The index of the value to return.
   * @return The bytes of the path at the given index.
   */
  com.google.protobuf.ByteString
      getPathBytes(int index);
}
