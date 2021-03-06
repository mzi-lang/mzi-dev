// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.parse;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.util.Assoc;
import org.aya.concrete.Decl;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.Stmt;
import org.aya.generic.Arg;
import org.aya.generic.Modifier;
import org.aya.parser.AyaBaseVisitor;
import org.aya.parser.AyaParser;
import org.aya.ref.LocalVar;
import org.aya.util.Constants;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.base.Traversable;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Either;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author ice1000, kiva
 */
public final class AyaProducer extends AyaBaseVisitor<Object> {
  private final @NotNull Reporter reporter;

  public AyaProducer(@NotNull Reporter reporter) {
    this.reporter = reporter;
  }

  @Override public ImmutableSeq<Stmt> visitProgram(AyaParser.ProgramContext ctx) {
    return ctx.stmt().stream().map(this::visitStmt).flatMap(Traversable::stream).collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull ImmutableSeq<Stmt> visitStmt(AyaParser.StmtContext ctx) {
    var importCmd = ctx.importCmd();
    if (importCmd != null) return ImmutableSeq.of(visitImportCmd(importCmd));
    var openCmd = ctx.openCmd();
    if (openCmd != null) return visitOpenCmd(openCmd);
    var decl = ctx.decl();
    if (decl != null) {
      var result = visitDecl(decl);
      return result._2.prepended(result._1);
    }
    var mod = ctx.module();
    if (mod != null) return ImmutableSeq.of(visitModule(mod));
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDecl(AyaParser.DeclContext ctx) {
    var accessibility = ctx.PRIVATE() == null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var fnDecl = ctx.fnDecl();
    if (fnDecl != null) return Tuple2.of(visitFnDecl(fnDecl, accessibility), ImmutableSeq.of());
    var dataDecl = ctx.dataDecl();
    if (dataDecl != null) return visitDataDecl(dataDecl, accessibility);
    var structDecl = ctx.structDecl();
    if (structDecl != null) return Tuple2.of(visitStructDecl(structDecl, accessibility), ImmutableSeq.of());
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public Decl.@NotNull FnDecl visitFnDecl(AyaParser.FnDeclContext ctx, Stmt.Accessibility accessibility) {
    var modifiers = ctx.fnModifiers().stream()
      .map(this::visitFnModifiers)
      .distinct()
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(Modifier.class)));
    var assocCtx = ctx.assoc();
    var abuseCtx = ctx.abuse();

    return new Decl.FnDecl(
      sourcePosOf(ctx.ID()),
      accessibility,
      modifiers,
      assocCtx == null ? null : visitAssoc(assocCtx),
      ctx.ID().getText(),
      visitTelescope(ctx.tele().stream()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitFnBody(ctx.fnBody()),
      abuseCtx == null ? ImmutableSeq.of() : visitAbuse(abuseCtx)
    );
  }

  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTelescope(Stream<AyaParser.TeleContext> stream) {
    return stream
      .map(this::visitTele)
      .flatMap(Traversable::stream)
      .collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull ImmutableSeq<@NotNull Stmt> visitAbuse(AyaParser.AbuseContext ctx) {
    return ctx.stmt().stream()
      .map(this::visitStmt)
      .flatMap(Traversable::stream)
      .collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull Either<Expr, ImmutableSeq<Pattern.Clause>> visitFnBody(AyaParser.FnBodyContext ctx) {
    var expr = ctx.expr();
    if (expr != null) return Either.left(visitExpr(expr));
    return Either.right(ctx.clause().stream().map(this::visitClause).collect(ImmutableSeq.factory()));
  }

  @Override
  public @NotNull Expr visitLiteral(AyaParser.LiteralContext ctx) {
    if (ctx.CALM_FACE() != null) return new Expr.HoleExpr(sourcePosOf(ctx), Constants.ANONYMOUS_PREFIX, null);
    var id = ctx.ID();
    if (id != null) return new Expr.UnresolvedExpr(sourcePosOf(id), id.getText());
    var universe = ctx.UNIVERSE();
    if (universe != null) {
      var universeText = universe.getText();
      var univTrunc = universeText.substring(1, universeText.indexOf("T"));
      var hLevel = switch (univTrunc) {
        default -> Integer.parseInt(univTrunc.substring(0, univTrunc.length() - 1));
        case "h-", "h" -> -3;
        case "" -> throw new UnsupportedOperationException("TODO");
        case "oo-" -> Integer.MAX_VALUE;
      };
      var uLevel = visitOptNumber(universeText.substring(universeText.indexOf("e") + 1), 0);
      return new Expr.UnivExpr(sourcePosOf(universe), uLevel, hLevel);
    }
    var set = ctx.SET_UNIV();
    if (set != null) {
      var text = set.getText().substring("\\Set".length());
      return new Expr.UnivExpr(sourcePosOf(set), visitOptNumber(text, 0), 0);
    }
    var prop = ctx.PROP();
    if (prop != null) return new Expr.UnivExpr(sourcePosOf(prop), 0, -1);
    if (ctx.LGOAL() != null) {
      var fillingExpr = ctx.expr();
      var filling = fillingExpr == null ? null : visitExpr(fillingExpr);
      return new Expr.HoleExpr(sourcePosOf(ctx), null, filling);
    }
    var number = ctx.NUMBER();
    if (number != null) return new Expr.LitIntExpr(sourcePosOf(number), Integer.parseInt(number.getText()));
    var string = ctx.STRING();
    if (string != null) return new Expr.LitStringExpr(sourcePosOf(string), string.getText());
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  public int visitOptNumber(@NotNull String number, int defaultVal) {
    return Optional.of(number)
      .filter(Predicate.not(String::isEmpty))
      .map(Integer::parseInt)
      .orElse(defaultVal);
  }

  @Override
  public @NotNull ImmutableSeq<Expr.@NotNull Param> visitTele(AyaParser.TeleContext ctx) {
    var literal = ctx.literal();
    if (literal != null)
      return ImmutableSeq.of(new Expr.Param(sourcePosOf(ctx), new LocalVar(Constants.ANONYMOUS_PREFIX), visitLiteral(literal), true));
    var teleMaybeTypedExpr = ctx.teleMaybeTypedExpr();
    if (ctx.LPAREN() != null) return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(true);
    assert ctx.LBRACE() != null;
    return visitTeleMaybeTypedExpr(teleMaybeTypedExpr).apply(false);
  }

  @Override
  public @NotNull Function<Boolean, ImmutableSeq<Expr.Param>> visitTeleMaybeTypedExpr(AyaParser.TeleMaybeTypedExprContext ctx) {
    var type = type(ctx.type(), sourcePosOf(ctx.ids()));
    return explicit -> visitIds(ctx.ids())
      .map(v -> new Expr.Param(v._1, new LocalVar(v._2), type, explicit))
      .collect(ImmutableSeq.factory());
  }

  public @NotNull Expr visitExpr(AyaParser.ExprContext ctx) {
    if (ctx instanceof AyaParser.AppContext app) return visitApp(app);
    if (ctx instanceof AyaParser.ProjContext proj) return visitProj(proj);
    if (ctx instanceof AyaParser.PiContext pi) return visitPi(pi);
    if (ctx instanceof AyaParser.SigmaContext sig) return visitSigma(sig);
    if (ctx instanceof AyaParser.LamContext lam) return visitLam(lam);
    if (ctx instanceof AyaParser.ArrContext arr) return visitArr(arr);
    // TODO: match
    throw new UnsupportedOperationException("TODO: " + ctx.getClass());
  }

  @Override
  public @NotNull Expr visitArr(AyaParser.ArrContext ctx) {
    var from = visitExpr(ctx.expr(0));
    var to = visitExpr(ctx.expr(1));
    return new Expr.PiExpr(
      sourcePosOf(ctx),
      false,
      new Expr.Param(sourcePosOf(ctx.expr(0)), new LocalVar(Constants.ANONYMOUS_PREFIX), from, true),
      to
    );
  }

  @Override
  public @NotNull Expr visitApp(AyaParser.AppContext ctx) {
    var argument = ctx.argument();
    final var atom = ctx.atom();
    if (argument.isEmpty()) return visitAtom(atom);
    return new Expr.AppExpr(
      sourcePosOf(ctx),
      visitAtom(atom),
      argument.stream()
        .map(this::visitArgument)
        .collect(ImmutableSeq.factory())
    );
  }

  @Override
  public @NotNull Expr visitAtom(AyaParser.AtomContext ctx) {
    var literal = ctx.literal();
    if (literal != null) return visitLiteral(literal);

    final var expr = ctx.expr();
    if (expr.size() == 1) return visitExpr(expr.get(0));
    return new Expr.TupExpr(
      sourcePosOf(ctx),
      expr.stream()
        .map(this::visitExpr)
        .collect(ImmutableSeq.factory())
    );
  }

  @Override
  public @NotNull Arg<Expr> visitArgument(AyaParser.ArgumentContext ctx) {
    var atom = ctx.atom();
    if (atom != null) return Arg.explicit(visitAtom(atom));
    if (ctx.LBRACE() != null) {
      var items = ctx.expr().stream()
        .map(this::visitExpr)
        .collect(ImmutableSeq.factory());
      if (items.sizeEquals(1)) return Arg.implicit(items.first());
      return Arg.implicit(new Expr.TupExpr(sourcePosOf(ctx), items));
    }
    // TODO: . idFix
    throw new UnsupportedOperationException();
  }

  @Override
  public Expr.@NotNull LamExpr visitLam(AyaParser.LamContext ctx) {
    return (Expr.LamExpr) buildLam(
      sourcePosOf(ctx),
      visitTelescope(ctx.tele().stream()).view(),
      visitLamBody(ctx)
    );
  }

  public static @NotNull Expr buildLam(
    SourcePos sourcePos,
    SeqView<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    return new Expr.LamExpr(
      sourcePos,
      params.first(),
      buildLam(sourcePosForSubExpr(params, body), params.drop(1), body)
    );
  }

  private @NotNull Expr visitLamBody(@NotNull AyaParser.LamContext ctx) {
    var bodyExpr = ctx.expr();

    if (bodyExpr == null) {
      var impliesToken = ctx.IMPLIES();
      var bodyHolePos = impliesToken == null
        ? sourcePosOf(ctx)
        : sourcePosOf(impliesToken);

      return new Expr.HoleExpr(bodyHolePos, null, null);
    }

    return visitExpr(bodyExpr);
  }

  @Override
  public Expr.@NotNull TelescopicSigmaExpr visitSigma(AyaParser.SigmaContext ctx) {
    return new Expr.TelescopicSigmaExpr(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele().stream()),
      visitExpr(ctx.expr())
    );
  }

  @Override
  public Expr.@NotNull PiExpr visitPi(AyaParser.PiContext ctx) {
    return (Expr.PiExpr) buildPi(
      sourcePosOf(ctx),
      false,
      visitTelescope(ctx.tele().stream()).view(),
      visitExpr(ctx.expr())
    );
  }

  public static @NotNull Expr buildPi(
    SourcePos sourcePos,
    boolean co,
    SeqView<Expr.Param> params,
    Expr body
  ) {
    if (params.isEmpty()) return body;
    var first = params.first();
    return new Expr.PiExpr(
      sourcePos,
      co,
      first,
      buildPi(sourcePosForSubExpr(params, body), co, params.drop(1), body)
    );
  }

  @NotNull private static SourcePos sourcePosForSubExpr(SeqView<Expr.Param> params, Expr body) {
    var restParamSourcePos = params.stream().skip(1)
      .map(Expr.Param::sourcePos)
      .reduce(SourcePos.NONE, (acc, it) -> {
        if (acc == SourcePos.NONE) return it;
        return new SourcePos(acc.tokenStartIndex(), it.tokenEndIndex(),
          acc.startLine(), acc.startColumn(), it.endLine(), it.endColumn());
      });
    var bodySourcePos = body.sourcePos();
    return new SourcePos(
      restParamSourcePos.tokenStartIndex(),
      bodySourcePos.tokenEndIndex(),
      restParamSourcePos.startLine(),
      restParamSourcePos.startColumn(),
      bodySourcePos.endLine(),
      bodySourcePos.endColumn()
    );
  }

  @Override
  public Expr.@NotNull ProjExpr visitProj(AyaParser.ProjContext proj) {
    return new Expr.ProjExpr(
      sourcePosOf(proj),
      visitExpr(proj.expr()),
      Integer.parseInt(proj.NUMBER().getText())
    );
  }

  public @NotNull Tuple2<Decl, ImmutableSeq<Stmt>> visitDataDecl(AyaParser.DataDeclContext ctx, Stmt.Accessibility accessibility) {
    var abuseCtx = ctx.abuse();
    var openAccessibility = ctx.PUBLIC() != null ? Stmt.Accessibility.Public : Stmt.Accessibility.Private;
    var data = new Decl.DataDecl(
      sourcePosOf(ctx.ID()),
      accessibility,
      ctx.ID().getText(),
      visitTelescope(ctx.tele().stream()),
      type(ctx.type(), sourcePosOf(ctx)),
      visitDataBody(ctx.dataBody()),
      abuseCtx == null ? ImmutableSeq.of() : visitAbuse(abuseCtx)
    );
    if (ctx.OPEN() != null) {
      return Tuple2.of(
        data,
        ImmutableSeq.of(new Stmt.OpenStmt(
          sourcePosOf(ctx),
          openAccessibility,
          ImmutableSeq.of(ctx.ID().getText()),
          Stmt.OpenStmt.UseHide.EMPTY
        ))
      );
    } else return Tuple2.of(data, ImmutableSeq.of());
  }

  public @NotNull Expr type(@Nullable AyaParser.TypeContext typeCtx, SourcePos sourcePos) {
    return typeCtx == null
      ? new Expr.HoleExpr(sourcePos, null, null)
      : visitType(typeCtx);
  }

  private @NotNull Either<Decl.DataDecl.Ctors, Decl.DataDecl.Clauses> visitDataBody(AyaParser.DataBodyContext ctx) {
    if (ctx instanceof AyaParser.DataCtorsContext dcc) return Either.left(visitDataCtors(dcc));
    if (ctx instanceof AyaParser.DataClausesContext dcc) return Either.right(visitDataClauses(dcc));

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public Decl.DataDecl.Ctors visitDataCtors(AyaParser.DataCtorsContext ctx) {
    return new Decl.DataDecl.Ctors(
      ctx.dataCtor().stream()
        .map(this::visitDataCtor)
        .collect(ImmutableSeq.factory())
    );
  }

  @Override
  public Decl.DataDecl.Clauses visitDataClauses(AyaParser.DataClausesContext ctx) {
    return new Decl.DataDecl.Clauses(ctx.dataCtorClause().stream()
      .map(this::visitDataCtorClause)
      .collect(ImmutableSeq.factory()));
  }

  @Override
  public Decl.@NotNull DataCtor visitDataCtor(AyaParser.DataCtorContext ctx) {
    var telescope = visitTelescope(ctx.tele().stream());
    var id = ctx.ID();

    return new Decl.DataCtor(
      sourcePosOf(id),
      id.getText(),
      telescope,
      visitClauses(ctx.clauses()),
      ctx.COERCE() != null
    );
  }

  @Override public ImmutableSeq<Pattern.Clause> visitClauses(@Nullable AyaParser.ClausesContext ctx) {
    if (ctx == null) return ImmutableSeq.empty();
    return ctx.clause().stream()
      .map(this::visitClause)
      .collect(ImmutableSeq.factory());
  }

  @Override public @NotNull Tuple2<@NotNull Pattern, Decl.@NotNull DataCtor>
  visitDataCtorClause(AyaParser.DataCtorClauseContext ctx) {
    return Tuple.of(
      visitPattern(ctx.pattern()),
      visitDataCtor(ctx.dataCtor())
    );
  }

  @Override
  public @NotNull Pattern visitPattern(AyaParser.PatternContext ctx) {
    var ex = ctx.LBRACE() == null;
    if (ex) return visitAtomPatterns(ctx.atomPatterns(0), true, null);

    var id = ctx.ID();
    var as = id != null ? new LocalVar(id.getText()) : null;
    var subs = ctx.atomPatterns().stream()
      .map(ap -> visitAtomPatterns(ap, false, as))
      .collect(ImmutableSeq.factory());

    return subs.sizeEquals(1)
      ? subs.first()
      : new Pattern.Tuple(sourcePosOf(ctx), false, subs, as);
  }

  private Pattern visitAtomPatterns(@NotNull AyaParser.AtomPatternsContext ctx, boolean ex, LocalVar as) {
    var atoms = ctx.atomPattern().stream()
      .map(this::visitAtomPattern).collect(ImmutableSeq.factory());
    if (atoms.sizeEquals(1)) return atoms.first().apply(ex);

    var first = atoms.first().apply(ex);
    if (!(first instanceof Pattern.Bind bind)) {
      reporter.report(new ParseError(first.sourcePos(),
        "`" + first.toDoc().renderWithPageWidth(114514) + "` is not a constructor name"));
      throw new ParsingInterruptedException();
    }
    return new Pattern.Ctor(
      sourcePosOf(ctx),
      ex,
      bind.bind().name(),
      atoms.view().drop(1).map(pa -> pa.apply(ex)).collect(ImmutableSeq.factory()),
      as
    );
  }

  @Override
  public @NotNull Function<Boolean, Pattern> visitAtomPattern(AyaParser.AtomPatternContext ctx) {
    if (ctx.LPAREN() != null) {
      var id = ctx.ID();
      var as = id != null ? new LocalVar(id.getText()) : null;
      return ex -> new Pattern.Tuple(sourcePosOf(ctx), ex, visitPatterns(ctx.patterns()), as);
    }
    if (ctx.CALM_FACE() != null) return ex -> new Pattern.CalmFace(sourcePosOf(ctx), ex);
    var number = ctx.NUMBER();
    if (number != null) return ex -> new Pattern.Number(sourcePosOf(ctx), ex, Integer.parseInt(number.getText()));
    var id = ctx.ID();
    if (id != null) return ex -> new Pattern.Bind(sourcePosOf(ctx), ex, new LocalVar(id.getText()), new Ref<>());

    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull ImmutableSeq<@NotNull Pattern> visitPatterns(AyaParser.PatternsContext ctx) {
    return ctx.pattern().stream()
      .map(this::visitPattern)
      .collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull Pattern.Clause visitClause(AyaParser.ClauseContext ctx) {
    if (ctx.ABSURD() != null) return new Pattern.Clause.Absurd();
    return new Pattern.Clause.Match(
      visitPatterns(ctx.patterns()),
      visitExpr(ctx.expr())
    );
  }

  public @NotNull Decl visitStructDecl(AyaParser.StructDeclContext ctx, Stmt.Accessibility accessibility) {
    // TODO: visit struct decl
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Expr visitType(@NotNull AyaParser.TypeContext ctx) {
    return visitExpr(ctx.expr());
  }

  @Override
  public @NotNull Stmt visitImportCmd(AyaParser.ImportCmdContext ctx) {
    final var id = ctx.ID();
    return new Stmt.ImportStmt(
      sourcePosOf(ctx),
      visitModuleName(ctx.moduleName()),
      id == null ? null : id.getText()
    );
  }

  @Override
  public @NotNull ImmutableSeq<Stmt> visitOpenCmd(AyaParser.OpenCmdContext ctx) {
    var accessibility = ctx.PUBLIC() == null
      ? Stmt.Accessibility.Private
      : Stmt.Accessibility.Public;
    var useHide = ctx.useHide();
    var modName = visitModuleName(ctx.moduleName());
    var open = new Stmt.OpenStmt(
      sourcePosOf(ctx),
      accessibility,
      modName,
      useHide != null ? visitUseHide(useHide) : Stmt.OpenStmt.UseHide.EMPTY
    );
    if (ctx.IMPORT() != null) return ImmutableSeq.of(
      new Stmt.ImportStmt(sourcePosOf(ctx), modName, null),
      open
    );
    else return ImmutableSeq.of(open);
  }

  public Stmt.OpenStmt.UseHide visitUse(List<AyaParser.UseContext> ctxs) {
    return new Stmt.OpenStmt.UseHide(
      ctxs.stream()
        .map(AyaParser.UseContext::useHideList)
        .map(AyaParser.UseHideListContext::ids)
        .flatMap(this::visitIds)
        .map(Tuple2::getValue)
        .collect(ImmutableSeq.factory()),
      Stmt.OpenStmt.UseHide.Strategy.Using);
  }

  public Stmt.OpenStmt.UseHide visitHide(List<AyaParser.HideContext> ctxs) {
    return new Stmt.OpenStmt.UseHide(
      ctxs.stream()
        .map(AyaParser.HideContext::useHideList)
        .map(AyaParser.UseHideListContext::ids)
        .flatMap(this::visitIds)
        .map(Tuple2::getValue)
        .collect(ImmutableSeq.factory()),
      Stmt.OpenStmt.UseHide.Strategy.Hiding);
  }

  @Override
  public @NotNull Stmt.OpenStmt.UseHide visitUseHide(@NotNull AyaParser.UseHideContext ctx) {
    var use = ctx.use();
    if (use != null) return visitUse(use);
    return visitHide(ctx.hide());
  }

  @Override
  public @NotNull Stmt.ModuleStmt visitModule(AyaParser.ModuleContext ctx) {
    return new Stmt.ModuleStmt(
      sourcePosOf(ctx),
      ctx.ID().getText(),
      ctx.stmt().stream().map(this::visitStmt)
        .flatMap(Traversable::stream)
        .collect(ImmutableSeq.factory())
    );
  }

  @Override
  public @NotNull Stream<Tuple2<SourcePos, String>> visitIds(AyaParser.IdsContext ctx) {
    return ctx.ID().stream().map(id -> Tuple.of(sourcePosOf(id), id.getText()));
  }

  @Override
  public @NotNull ImmutableSeq<@NotNull String> visitModuleName(AyaParser.ModuleNameContext ctx) {
    return ctx.ID().stream()
      .map(ParseTree::getText)
      .collect(ImmutableSeq.factory());
  }

  @Override
  public @NotNull Assoc visitAssoc(AyaParser.AssocContext ctx) {
    if (ctx.FIX() != null) return Assoc.Fix;
    if (ctx.FIXL() != null) return Assoc.FixL;
    if (ctx.FIXR() != null) return Assoc.FixR;
    if (ctx.INFIX() != null) return Assoc.Infix;
    if (ctx.INFIXL() != null) return Assoc.InfixL;
    if (ctx.INFIXR() != null) return Assoc.InfixR;
    if (ctx.TWIN() != null) return Assoc.Twin;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  @Override
  public @NotNull Modifier visitFnModifiers(AyaParser.FnModifiersContext ctx) {
    if (ctx.ERASE() != null) return Modifier.Erase;
    if (ctx.INLINE() != null) return Modifier.Inline;
    throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }

  private @NotNull SourcePos sourcePosOf(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var end = ctx.getStop();
    return new SourcePos(
      start.getStartIndex(),
      end.getStopIndex(),
      start.getLine(),
      start.getCharPositionInLine(),
      end.getLine(),
      end.getCharPositionInLine() + end.getText().length() - 1
    );
  }

  private @NotNull SourcePos sourcePosOf(TerminalNode node) {
    var token = node.getSymbol();
    var line = token.getLine();
    return new SourcePos(
      token.getStartIndex(),
      token.getStopIndex(),
      line,
      token.getCharPositionInLine(),
      line,
      token.getCharPositionInLine() + token.getText().length() - 1
    );
  }
}
