package org.mzi.generic;

/**
 * @author ice1000
 */
public enum DTKind {
  Pi(true, true), Sigma(false, true),
  Copi(true, false), Cosigma(false, false);

  public final boolean function, forward;

  DTKind(boolean function, boolean forward) {
    this.function = function;
    this.forward = forward;
  }
}