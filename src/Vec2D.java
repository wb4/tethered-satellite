class Vec2D {
  private double x;
  private double y;

  public Vec2D() {
    this(0.0, 0.0);
  }

  public Vec2D(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public double x() {
    return x;
  }

  public double y() {
    return y;
  }

  public Vec2D add(Vec2D other) {
    return new Vec2D(x + other.x, y + other.y);
  }

  public Vec2D sub(Vec2D other) {
    return new Vec2D(x - other.x, y - other.y);
  }

  public Vec2D scale(double s) {
    return new Vec2D(s * x, s * y);
  }

  public Vec2D normalized() {
    return scale(1.0 / length());
  }

  public Vec2D flip() {
    return scale(-1.0);
  }

  public Vec2D toLength(double newLength) {
    return scale(newLength / length());
  }

  public double dot(Vec2D other) {
    return x * other.x + y * other.y;
  }

  public double cross(Vec2D other) {
    return x * other.y - y * other.x;
  }

  public Vec2D rotate(double radians) {
    double sin = Math.sin(radians);
    double cos = Math.cos(radians);

    return new Vec2D(x * cos - y * sin, x * sin + y * cos);
  }

  public Vec2D componentParallelTo(Vec2D other) {
    return other.scale(dot(other) / other.lengthSquared());
  }

  public Vec2D componentPerpendicularTo(Vec2D other) {
    return sub(componentParallelTo(other));
  }

  public double length() {
    return Math.sqrt(lengthSquared());
  }

  public double lengthSquared() {
    return x * x + y * y;
  }

  public double distanceTo(Vec2D other) {
    return Math.sqrt(distanceSquaredTo(other));
  }

  public double distanceSquaredTo(Vec2D other) {
    return sub(other).lengthSquared();
  }

  public String toString() {
    return "Vec2D { " + x + ", " + y + " }";
  }
}
