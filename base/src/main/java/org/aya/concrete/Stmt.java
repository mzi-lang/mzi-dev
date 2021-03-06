// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.SourcePos;
import org.aya.concrete.pretty.StmtPrettyConsumer;
import org.aya.concrete.resolve.visitor.StmtResolver;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
public sealed interface Stmt permits Decl, Stmt.ImportStmt, Stmt.ModuleStmt, Stmt.OpenStmt {
  @Contract(pure = true) @NotNull SourcePos sourcePos();

  /** @apiNote the \import stmts do not have a meaningful accessibility, do not refer to this in those cases */
  @Contract(pure = true) @NotNull Accessibility accessibility();

  default void resolve() {
    accept(StmtResolver.INSTANCE, Unit.unit());
  }

  default @NotNull Doc toDoc() {
    return accept(StmtPrettyConsumer.INSTANCE, Unit.unit());
  }

  /**
   * @author re-xyr
   */
  interface Visitor<P, R> extends Decl.Visitor<P, R> {
    default void traceEntrance(@NotNull Stmt stmt, P p) {
    }
    @ApiStatus.NonExtendable
    @Override default void traceEntrance(@NotNull Decl decl, P p) {
      traceEntrance((Stmt) decl, p);
    }
    default @NotNull ImmutableSeq<R> visitAll(@NotNull ImmutableSeq<@NotNull Stmt> stmts, P p) {
      return stmts.map(stmt -> stmt.accept(this, p));
      // [xyr]: Is this OK? The order of visiting must be preserved.
      // [ice]: I guess so, map should preserve the order.
    }
    R visitImport(@NotNull ImportStmt cmd, P p);
    R visitOpen(@NotNull OpenStmt cmd, P p);
    R visitModule(@NotNull ModuleStmt mod, P p);
  }

  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }

  /**
   * @author re-xyr
   */
  enum Accessibility {
    Private,
    Public;

    public boolean lessThan(Accessibility accessibility) {
      return ordinal() < accessibility.ordinal();
    }
  }

  /**
   * @author re-xyr
   */
  final record ModuleStmt(
    @NotNull SourcePos sourcePos,
    @NotNull String name,
    @NotNull ImmutableSeq<@NotNull Stmt> contents
  ) implements Stmt {

    @Override
    public @NotNull Accessibility accessibility() {
      return Accessibility.Public;
    }

    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitModule(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  final record ImportStmt(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<String> path,
    @Nullable String asName
  ) implements Stmt {

    @Override
    public @NotNull Accessibility accessibility() {
      return Accessibility.Private;
    }

    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitImport(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  final record OpenStmt(
    @NotNull SourcePos sourcePos,
    @NotNull Accessibility accessibility,
    @NotNull ImmutableSeq<String> path,
    @NotNull UseHide useHide
  ) implements Stmt {
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitOpen(this, p);
    }

    /**
     * @author re-xyr
     */
    public record UseHide(@NotNull ImmutableSeq<@NotNull String> list, @NotNull UseHide.Strategy strategy) {
      public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), UseHide.Strategy.Hiding);

      public boolean uses(String name) {
        return switch (strategy) {
          case Using -> list.contains(name);
          case Hiding -> !list.contains(name);
        };
      }

      /**
       * @author re-xyr
       */
      public enum Strategy {
        Using,
        Hiding,
      }
    }
  }
}
