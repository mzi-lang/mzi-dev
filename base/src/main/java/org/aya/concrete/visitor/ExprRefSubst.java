// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.visitor;

import org.aya.api.error.Reporter;
import org.aya.api.ref.Var;
import org.aya.concrete.Expr;
import org.aya.concrete.resolve.error.UnqualifiedNameNotFoundError;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record ExprRefSubst(
  @NotNull Reporter reporter,
  @NotNull MutableHashMap<Var, Var> good,
  @NotNull MutableHashSet<Var> bad
) implements ExprFixpoint<Unit> {
  @Override public @NotNull Expr visitRef(@NotNull Expr.RefExpr expr, Unit unit) {
    var v = expr.resolvedVar();
    if (bad.contains(v)) {
      reporter.report(new UnqualifiedNameNotFoundError(v.name(), expr.sourcePos()));
      throw new ExprTycker.TyckInterruptedException();
    }
    var rv = good.getOption(v);
    if (rv.isDefined()) return new Expr.RefExpr(expr.sourcePos(), rv.get());
    else return expr;
  }

  public void clear() {
    good.clear();
    bad.clear();
  }
}
