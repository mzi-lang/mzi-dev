module org.aya {
  requires static org.jetbrains.annotations;
  requires static org.antlr.antlr4.runtime;

  requires transitive org.aya.api;
  requires transitive org.aya.parser;
  requires transitive org.aya.pretty;
  requires org.glavo.kala.base;
  requires org.glavo.kala.collection;

  exports org.aya.concrete.parse;
  exports org.aya.concrete.resolve.context;
  exports org.aya.concrete.resolve.module;
  exports org.aya.concrete.visitor;
  exports org.aya.concrete;
  exports org.aya.core.def;
  exports org.aya.core.pat;
  exports org.aya.core.term;
  exports org.aya.core.visitor;
  exports org.aya.core;
  exports org.aya.generic;
  exports org.aya.ref;
  exports org.aya.tyck.pat;
  exports org.aya.tyck.sort;
  exports org.aya.tyck.trace;
  exports org.aya.tyck.unify;
  exports org.aya.tyck;
  exports org.aya.util.cancel;
  exports org.aya.util;
}
