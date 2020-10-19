package org.mzi.core.visitor;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.mzi.core.term.*;

/**
 * @author ice1000
 */
public interface BaseTermVisitor<P> extends Term.Visitor<P, @NotNull Term> {
  @Override default @NotNull Term visitLam(@NotNull LamTerm term, P p) {
    return new LamTerm(ImmutableSeq.from(term.binds()), term.body().accept(this, p));
  }

  @Override default @NotNull Term visitUniv(@NotNull UnivTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitPi(@NotNull PiTerm term, P p) {
    return new PiTerm(ImmutableSeq.from(term.binds()), term.body().accept(this, p));
  }

  @Override default @NotNull Term visitRef(@NotNull RefTerm term, P p) {
    return term;
  }

  @Override default @NotNull Term visitApp(@NotNull AppTerm.Apply term, P p) { return term; }
}
