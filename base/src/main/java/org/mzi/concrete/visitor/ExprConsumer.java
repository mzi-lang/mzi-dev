// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.visitor;

import asia.kala.Unit;
import asia.kala.collection.mutable.Buffer;
import asia.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.concrete.Param;
import org.mzi.generic.Arg;
import org.mzi.core.Tele;

public interface ExprConsumer<P> extends Expr.Visitor<P, Unit>, Tele.Visitor<Expr, P, Unit> {
  @Override default Unit visitNamed(Tele.@NotNull NamedTele<Expr> named, P p) {
    return named.next().accept(this, p);
  }

  @Override default Unit visitTyped(Tele.@NotNull TypedTele<Expr> typed, P p) {
    Option.of(typed.next()).forEach(tele -> tele.accept(this, p));
    return typed.type().accept(this, p);
  }

  @Override default Unit visitRef(Expr.@NotNull RefExpr expr, P p) {
    return Unit.unit();
  }

  @Override default Unit visitUnresolved(Expr.@NotNull UnresolvedExpr expr, P p) {
    return Unit.unit();
  }

  @Override
  default Unit visitHole(Expr.@NotNull HoleExpr holeExpr, P p) {
    var expr = holeExpr.filling();
    if (expr != null) expr.accept(this, p);
    return Unit.unit();
  }

  @Override default Unit visitUniv(Expr.@NotNull UnivExpr expr, P p) {
    return Unit.unit();
  }

  default void visitArg(@NotNull Arg<Expr> arg, P p) {
    arg.term().accept(this, p);
  }

  @Override default Unit visitApp(Expr.@NotNull AppExpr expr, P p) {
    expr.argument().forEach(arg -> visitArg(arg, p));
    return expr.function().accept(this, p);
  }

  default Unit visitParams(Buffer<Param> params, P p) {
    params.forEach(param -> param.type().accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitDT(Expr.@NotNull DTExpr expr, P p) {
    visitParams(expr.params(), p);
    return expr.last().accept(this, p);
  }

  @Override default Unit visitLam(Expr.@NotNull LamExpr expr, P p) {
    visitParams(expr.params(), p);
    return expr.body().accept(this, p);
  }

  @Override default Unit visitTup(Expr.@NotNull TupExpr expr, P p) {
    expr.items().forEach(item -> item.accept(this, p));
    return Unit.unit();
  }

  @Override default Unit visitProj(Expr.@NotNull ProjExpr expr, P p) {
    return expr.tup().accept(this, p);
  }
}
