// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.Global;
import org.aya.api.error.SourcePos;
import org.aya.concrete.parse.AyaProducer;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.ref.LocalVar;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.immutable.ImmutableVector;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.control.Either;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParseTest {
  @BeforeAll public static void enableTest() {
    Global.enterTestMode();
  }

  @Test
  public void issue141() {
    Assertions.assertEquals(AyaProducer.parseStmt("\\module a {}"),
      ImmutableSeq.of(new Stmt.ModuleStmt(SourcePos.NONE, "a", ImmutableSeq.empty())));
  }

  @Test
  public void successCmd() {
    parseOpen("\\open A");
    parseOpen("\\open A.B");
    parseOpen("\\open A \\using ()");
    parseOpen("\\open A \\hiding ()");
    parseImport("\\import A");
    parseImport("\\import A.B");
    parseImport("\\import A.B \\using ()");
    parseTo("\\open Boy.Next.Door \\using (door) \\using (next)", ImmutableSeq.of(new Stmt.OpenStmt(
      SourcePos.NONE,
      Stmt.Accessibility.Private,
      ImmutableSeq.of("Boy", "Next", "Door"),
      new Stmt.OpenStmt.UseHide(ImmutableVector.of("door", "next"), Stmt.OpenStmt.UseHide.Strategy.Using)
    )));
  }

  @Test
  public void successLiteral() {
    assertTrue(AyaProducer.parseExpr("diavolo") instanceof Expr.UnresolvedExpr);
    parseUniv("\\Prop");
    parseUniv("\\Set");
    parseUniv("\\Set0");
    parseUniv("\\Set233");
    parseUniv("\\2-Type");
    parseUniv("\\2-Type2");
    parseUniv("\\114-Type514");
    parseUniv("\\hType2");
    parseUniv("\\h-Type2");
    parseUniv("\\oo-Type2");
  }

  @Test
  public void successDecl() {
    parseFn("\\def a => 1");
    parseFn("\\def a (b : X) => b");
    parseFn("\\def a (f : \\Pi a b c d -> a) => b");
    parseFn("\\def a (t : \\Sig a b ** s) => b");
    parseFn("""
      \\def uncurry (A : \\Set) (B : \\Set) (C : \\Set)
                   (f : \\Pi A B -> C)
                   (p : \\Sig A ** B) : C
        => f p.1 p.2""");
    parseData("\\data Unit");
    parseData("\\data Unit \\abusing {}");
    parseData("\\data Unit : A \\abusing {}");
    parseData("\\data T {A : \\114-Type514} : A \\abusing {}");
    final var A = new Expr.Param(SourcePos.NONE, new LocalVar("A"), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false);
    final var a = new Expr.Param(SourcePos.NONE, new LocalVar("a"), new Expr.UnresolvedExpr(SourcePos.NONE, "A"), true);
    parseTo("\\def id {A : \\114-Type514} (a : A) : A => a", ImmutableSeq.of(new Decl.FnDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      EnumSet.noneOf(Modifier.class),
      null,
      "id",
      ImmutableSeq.of(A, a),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      ImmutableSeq.of()
    )));
    final var b = new Expr.Param(SourcePos.NONE, new LocalVar("B"), new Expr.UnivExpr(SourcePos.NONE, 514, 114), false);
    parseTo("\\def xx {A, B : \\114-Type514} (a : A) : A => a", ImmutableSeq.of(new Decl.FnDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      EnumSet.noneOf(Modifier.class),
      null,
      "xx",
      ImmutableSeq.of(A, b, a),
      new Expr.UnresolvedExpr(SourcePos.NONE, "A"),
      new Expr.UnresolvedExpr(SourcePos.NONE, "a"),
      ImmutableSeq.of()
    )));
    parseTo("\\data Nat | Z | S Nat", ImmutableSeq.of(new Decl.DataDecl(
      SourcePos.NONE,
      Stmt.Accessibility.Public,
      "Nat",
      ImmutableSeq.of(),
      new Expr.HoleExpr(SourcePos.NONE, null, null),
      Either.left(new Decl.DataDecl.Ctors(Buffer.of(
        new Decl.DataCtor(SourcePos.NONE, "Z", ImmutableSeq.of(), Buffer.of(), Buffer.of(), false),
        new Decl.DataCtor(SourcePos.NONE, "S",
          ImmutableSeq.of(
            new Expr.Param(SourcePos.NONE, new LocalVar("_"), new Expr.UnresolvedExpr(SourcePos.NONE, "Nat"), true)
          ),
          Buffer.of(), Buffer.of(), false
        )
      ))),
      ImmutableSeq.of()
    )));
  }

  @Test
  public void successExpr() {
    assertTrue(AyaProducer.parseExpr("boy") instanceof Expr.UnresolvedExpr);
    assertTrue(AyaProducer.parseExpr("f a") instanceof Expr.AppExpr);
    assertTrue(AyaProducer.parseExpr("f a b c") instanceof Expr.AppExpr);
    assertTrue(AyaProducer.parseExpr("a.1") instanceof Expr.ProjExpr);
    assertTrue(AyaProducer.parseExpr("a.1.2") instanceof Expr.ProjExpr);
    assertTrue(AyaProducer.parseExpr("f (a.1) (a.2)") instanceof Expr.AppExpr app
      && app.arguments().get(0).term() instanceof Expr.ProjExpr
      && app.arguments().get(1).term() instanceof Expr.ProjExpr);
    assertTrue(AyaProducer.parseExpr("λ a => a") instanceof Expr.LamExpr);
    assertTrue(AyaProducer.parseExpr("\\lam a => a") instanceof Expr.LamExpr);
    assertTrue(AyaProducer.parseExpr("\\lam a b => a") instanceof Expr.LamExpr);
    assertTrue(AyaProducer.parseExpr("Π a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(AyaProducer.parseExpr("\\Pi a -> a") instanceof Expr.PiExpr dt && !dt.co());
    assertTrue(AyaProducer.parseExpr("\\Pi a b -> a") instanceof Expr.PiExpr dt
      && !dt.co() && dt.last() instanceof Expr.PiExpr);
    assertTrue(AyaProducer.parseExpr("Σ a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(AyaProducer.parseExpr("\\Sig a ** b") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(AyaProducer.parseExpr("\\Sig a b ** c") instanceof Expr.TelescopicSigmaExpr dt && !dt.co());
    assertTrue(AyaProducer.parseExpr("\\Pi (x : \\Sig a ** b) -> c") instanceof Expr.PiExpr dt && !dt.co() && dt.param().type() instanceof Expr.TelescopicSigmaExpr);
    parseTo("f a . 1", new Expr.ProjExpr(
      SourcePos.NONE,
      new Expr.AppExpr(
        SourcePos.NONE,
        new Expr.UnresolvedExpr(SourcePos.NONE, "f"),
        ImmutableSeq.of(Arg.explicit(new Expr.UnresolvedExpr(SourcePos.NONE, "a")))
      ),
      1
    ));
    assertTrue(AyaProducer.parseExpr("f (a, b, c)") instanceof Expr.AppExpr app
      && app.arguments().sizeEquals(1)
      && app.arguments().get(0).term() instanceof Expr.TupExpr tup
      && tup.items().sizeEquals(3));
  }

  private void parseImport(@Language("TEXT") String code) {
    assertTrue(AyaProducer.parseStmt(code).first() instanceof Stmt.ImportStmt s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseOpen(@Language("TEXT") String code) {
    assertTrue(AyaProducer.parseStmt(code).last() instanceof Stmt.OpenStmt s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseFn(@Language("TEXT") String code) {
    assertTrue(AyaProducer.parseDecl(code)._1 instanceof Decl.FnDecl s && !s.toDoc().renderWithPageWidth(114514).isEmpty());
  }

  private void parseData(@Language("TEXT") String code) {
    assertTrue(AyaProducer.parseDecl(code)._1 instanceof Decl.DataDecl);
  }

  private void parseUniv(@Language("TEXT") String code) {
    assertTrue(AyaProducer.parseExpr(code) instanceof Expr.UnivExpr);
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, ImmutableSeq<Stmt> stmt) {
    assertEquals(stmt, AyaProducer.parseStmt(code));
  }

  private void parseTo(@NotNull @NonNls @Language("TEXT") String code, Expr expr) {
    Assertions.assertEquals(expr, AyaProducer.parseExpr(code));
  }
}
