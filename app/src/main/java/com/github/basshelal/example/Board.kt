package com.github.basshelal.example

data class ListItem<T>(
        var id: Long,
        var value: T
)

typealias StringListItem = ListItem<String>

data class BoardList<T>(
        var id: Long,
        var name: String,
        val items: MutableList<ListItem<T>>
) {
    operator fun get(index: Int) = items[index]
    operator fun set(index: Int, value: ListItem<T>) {
        items[index] = value
    }
}

data class Board<T>(
        var id: Long,
        var name: String,
        val boardLists: MutableList<BoardList<T>>
) {
    operator fun get(index: Int) = boardLists[index]
    operator fun set(index: Int, value: BoardList<T>) {
        boardLists[index] = value
    }
}

val boardListsSize = 100
val itemsSize = 10

val EXAMPLE_BOARD = Board<String>(
        id = 69420,
        name = "My Board",
        boardLists = MutableList(boardListsSize) { listNumber ->
            BoardList(
                    id = listNumber.toLong(),
                    name = "List #$listNumber",
                    items = MutableList(itemsSize) { itemNumber ->
                        StringListItem(
                                id = ((listNumber * boardListsSize) + itemNumber).toLong(),
                                value = "Item #${(listNumber * boardListsSize) + itemNumber}")
                    })
        })