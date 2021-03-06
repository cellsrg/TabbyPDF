package ru.icc.cells.tabbypdf.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.geom.Rectangle2D;

/**
 * Simple rectangular bounding, represented with four coordinates:
 * left, right x-coordinates and top, bottom y-coordinates
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rectangle {
    private double left;
    private double bottom;
    private double right;
    private double top;

    /**
     * Joins this rectangle with another rectangle. Result keeps in this rectangle
     */
    protected <T extends Rectangle> void join(T other) {
        left   = Double.min(this.left, other.getLeft());
        bottom = Double.min(this.bottom, other.getBottom());
        right  = Double.max(this.right, other.getRight());
        top    = Double.max(this.top, other.getTop());
    }

    /**
     * Checks whether this rectangle contains other rectangle
     */
    public <T extends Rectangle> boolean contains(T other) {
        Rectangle2D.Double thisRect  = new Rectangle2D.Double(left, bottom, right - left, top - bottom);
        Rectangle2D.Double otherRect = new Rectangle2D.Double(
            other.getLeft(),
            other.getBottom(),
            other.getRight() - other.getLeft(),
            other.getTop() - other.getBottom()
        );
        return thisRect.contains(otherRect);
    }

    public <T extends Rectangle> boolean intersects(T other) {
        double delta = 0.001;
        Rectangle2D.Double thisRect  = new Rectangle2D.Double(left, bottom, right - left + delta, top - bottom + delta);
        Rectangle2D.Double otherRect = new Rectangle2D.Double(
            other.getLeft(),
            other.getBottom(),
            other.getRight() - other.getLeft() + delta,
            other.getTop() - other.getBottom() + delta
        );
        return thisRect.intersects(otherRect);
    }

    public void rotate(int rotation, double pageHeight, double pageWidth) {
        switch (rotation) {
            case 270:
                rotate(pageHeight);
                // fall through
            case 180:
                rotate(pageWidth);
                // fall through
            case 90:
                rotate(pageHeight);
                // fall through
            case 0:
                break;
            default:
                throw new IllegalArgumentException("Rotation should be 0, 90, 180 or 270");
        }
    }

    private void rotate(double height) {
        double old = bottom;
        bottom = left;
        left = height - old;

        old = right;
        right = height - top;
        top = old;
    }

    public static Rectangle fromRuling(Ruling ruling) {
        double left   = Double.min(ruling.getStartLocation().getX(), ruling.getEndLocation().getX());
        double right  = Double.max(ruling.getStartLocation().getX(), ruling.getEndLocation().getX());
        double bottom = Double.min(ruling.getStartLocation().getY(), ruling.getEndLocation().getY());
        double top    = Double.max(ruling.getStartLocation().getY(), ruling.getEndLocation().getY());

        return new Rectangle(left, bottom, right, top);
    }
}
