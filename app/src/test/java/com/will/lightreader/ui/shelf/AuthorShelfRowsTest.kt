package com.will.lightreader.ui.shelf

import org.junit.Assert.*
import org.junit.Test

class AuthorShelfRowsTest {
    private fun book(key: String, author: String) = ShelfBook(key,"test",key,key,author,null,0,0,"","",continueUrl=null,continueTitle="")
    @Test fun `collapse removes only its books and preserves every other row key`() {
        val groups=listOf(AuthorGroup("A",listOf(book("1","A"),book("2","A"))),AuthorGroup("B",listOf(book("3","B"))))
        val all=authorShelfRows(groups,setOf("A","B"));val folded=authorShelfRows(groups,setOf("B"))
        assertEquals(listOf("author:A","author:B","author-book:3"),folded.map{it.key})
        assertEquals(all.map{it.key}.filterNot{it in setOf("author-book:1","author-book:2")},folded.map{it.key})
        assertEquals(all.size,all.map{it.key}.distinct().size)
    }
    @Test fun `large collapsed author has no eager book rows and opens in reading order`() {
        val books=(1..1000).map{book(it.toString(),"A")};val groups=listOf(AuthorGroup("A",books))
        assertEquals(1,authorShelfRows(groups,emptySet()).size)
        assertEquals(books,authorShelfRows(groups,setOf("A")).filterIsInstance<AuthorShelfRow.Book>().map{it.book})
    }
}
