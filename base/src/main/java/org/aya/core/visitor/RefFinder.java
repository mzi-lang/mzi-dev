// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.Var;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr, ice1000
 * @see RefFinder#HEADER_ONLY
 * @see RefFinder#HEADER_AND_BODY
 */
public record RefFinder(boolean withBody) implements
  Def.Visitor<@NotNull Buffer<Def>, Unit>,
  Pat.Clause.Visitor<@NotNull Buffer<Def>, Unit> {
  private static final class TermRefFinder implements VarConsumer<@NotNull Buffer<Def>> {
    public static final @NotNull TermRefFinder INSTANCE = new TermRefFinder();

    @Override public void visitVar(Var usage, @NotNull Buffer<Def> defs) {
      if (usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def) defs.append(def);
    }
  }

  public static RefFinder HEADER_ONLY = new RefFinder(false);
  public static RefFinder HEADER_AND_BODY = new RefFinder(true);

  @Override public Unit visitFn(@NotNull FnDef fn, @NotNull Buffer<Def> references) {
    tele(references, fn.telescope());
    fn.result().accept(TermRefFinder.INSTANCE, references);
    if (withBody) fn.body().map(
      term -> term.accept(TermRefFinder.INSTANCE, references),
      clauses -> {
        clauses.forEach(clause -> clause.accept(this, references));
        return Unit.unit();
      });
    return Unit.unit();
  }

  @Override public Unit visitCtor(@NotNull DataDef.Ctor def, @NotNull Buffer<Def> references) {
    tele(references, def.conTelescope());
    if (withBody) for (var clause : def.clauses()) clause.accept(this, references);
    return Unit.unit();
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Override public Unit visitData(@NotNull DataDef def, @NotNull Buffer<Def> references) {
    tele(references, def.telescope());
    def.result().accept(TermRefFinder.INSTANCE, references);
    def.clauses().forEach((pat, ctor) -> ctor.accept(this, references));
    def.ctors().forEach(ctor -> ctor.accept(this, references));
    return Unit.unit();
  }

  @Override public Unit visitMatch(Pat.Clause.@NotNull Match match, @NotNull Buffer<Def> defs) {
    match.expr().accept(TermRefFinder.INSTANCE, defs);
    return Unit.unit();
  }

  @Override public Unit visitAbsurd(@NotNull Buffer<Def> defs) {
    return Unit.unit();
  }

  private void tele(@NotNull Buffer<Def> references, @NotNull SeqLike<Term.Param> telescope) {
    telescope.forEach(param -> param.type().accept(TermRefFinder.INSTANCE, references));
  }
}
