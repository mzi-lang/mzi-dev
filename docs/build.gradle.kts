// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

tasks.register<org.aya.gradle.PreprocessZhihuTask>("zhihu") {
  from(file("src"))
  into(file("zhihu"))
}
