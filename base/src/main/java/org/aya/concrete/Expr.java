// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.concrete.pretty.ExprPrettyConsumer;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.context.EmptyContext;
import org.aya.concrete.resolve.visitor.ExprResolver;
import org.aya.generic.Arg;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author re-xyr
 */
public sealed interface Expr {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret, this, p);
    return ret;
  }

  @NotNull SourcePos sourcePos();

  default @NotNull Expr resolve(@NotNull Context context) {
    return accept(ExprResolver.INSTANCE, context);
  }

  default @NotNull Expr resolve(Reporter reporter) {
    return resolve(new EmptyContext(reporter));
  }

  default @NotNull Doc toDoc() {
    return accept(ExprPrettyConsumer.INSTANCE, false);
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Expr expr, P p) {
    }
    default void traceExit(R r, @NotNull Expr expr, P p) {
    }
    R visitRef(@NotNull RefExpr expr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitPi(@NotNull Expr.PiExpr expr, P p);
    R visitTelescopicSigma(@NotNull Expr.TelescopicSigmaExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr expr, P p);
    R visitTup(@NotNull TupExpr expr, P p);
    R visitProj(@NotNull ProjExpr expr, P p);
    R visitLitInt(@NotNull LitIntExpr expr, P p);
    R visitLitString(@NotNull LitStringExpr expr, P p);
  }

  interface BaseVisitor<P, R> extends Visitor<P, R> {
    R catchUnhandled(@NotNull Expr expr, P p);
    @Override default R visitRef(@NotNull RefExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitUnresolved(@NotNull UnresolvedExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLam(@NotNull LamExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitPi(@NotNull Expr.PiExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitTelescopicSigma(@NotNull Expr.TelescopicSigmaExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitUniv(@NotNull UnivExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitApp(@NotNull AppExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitHole(@NotNull HoleExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitTup(@NotNull TupExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitProj(@NotNull ProjExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitInt(@NotNull LitIntExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitString(@NotNull LitStringExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
  }

  /**
   * @author re-xyr
   */
  record UnresolvedExpr(
    @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnresolved(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @NotNull SourcePos sourcePos,
    @Nullable String name,
    @Nullable Expr filling
  ) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }
  }

  /**
   * @author ice1000
   */
  interface TelescopicExpr {
    @NotNull ImmutableSeq<Param> params();

    default @NotNull Stream<@NotNull Tuple2<@NotNull Var, Param>> paramsStream() {
      return params().stream().map(p -> Tuple.of(p.ref(), p));
    }
  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull ImmutableSeq<@NotNull Arg<Expr>> arguments
  ) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record PiExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull Expr.Param param,
    @NotNull Expr last
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record LamExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr.Param param,
    @NotNull Expr body
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record TelescopicSigmaExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull ImmutableSeq<@NotNull Param> params,
    @NotNull Expr last
  ) implements Expr, TelescopicExpr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTelescopicSigma(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Var resolvedVar
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRef(this, p);
    }
  }

  /**
   * @param hLevel specified hLevel
   * @param uLevel specified uLevel
   * @author re-xyr, ice1000
   */
  record UnivExpr(
    @NotNull SourcePos sourcePos,
    int uLevel,
    int hLevel
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record TupExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    int ix
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }
  }

  /**
   * @author kiva
   */
  record LitIntExpr(
    @NotNull SourcePos sourcePos,
    int integer
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitInt(this, p);
    }
  }

  record LitStringExpr(
    @NotNull SourcePos sourcePos,
    @NotNull String string
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitString(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar ref,
    @Nullable Expr type,
    boolean explicit
  ) implements ParamLike<Expr> {
    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, null, explicit);
    }

    public @NotNull Expr.Param mapExpr(@NotNull Function<@Nullable Expr, @Nullable Expr> mapper) {
      return new Param(sourcePos, ref, mapper.apply(type), explicit);
    }
  }
}
