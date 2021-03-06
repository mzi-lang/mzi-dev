module org.aya.pretty {
  requires static org.jetbrains.annotations;

  requires org.glavo.kala.base;
  requires org.glavo.kala.collection;

  exports org.aya.pretty.backend;
  exports org.aya.pretty.doc;
  exports org.aya.pretty.error;
  exports org.aya.pretty.printer;
}
