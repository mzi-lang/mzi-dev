// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.util.Decision;
import org.glavo.kala.collection.SeqLike;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(boolean co, @NotNull Term.Param param, @NotNull Term body) implements Term {
  @Override @Contract(pure = true) public @NotNull Decision whnf() {
    return Decision.YES;
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitPi(this, p);
  }

  @Override public <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    return visitor.visitPi(this, p, q);
  }

  public static @NotNull Term make(boolean co, @NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.view().reversed().foldLeft(body, (t, p) -> new PiTerm(co, p, t));
  }
}
