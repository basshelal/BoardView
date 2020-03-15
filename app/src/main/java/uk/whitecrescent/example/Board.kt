package uk.whitecrescent.example

data class ListItem<T>(
        var id: Int,
        var value: T
)

typealias StringListItem = ListItem<String>

data class BoardList<T>(
        var id: Int,
        var name: String,
        val items: MutableList<ListItem<T>>
) {
    operator fun get(index: Int) = items[index]
    operator fun set(index: Int, value: ListItem<T>) {
        items[index] = value
    }
}

data class Board<T>(
        var id: Int,
        var name: String,
        val boardLists: MutableList<BoardList<T>>
) {
    operator fun get(index: Int) = boardLists[index]
    operator fun set(index: Int, value: BoardList<T>) {
        boardLists[index] = value
    }
}

val boardListsSize = 100
val itemsSize = 100

val exampleBoard = Board<String>(
        id = 69420,
        name = "My Board",
        boardLists = MutableList(boardListsSize) { listNumber ->
            BoardList(
                    id = listNumber,
                    name = "List #$listNumber",
                    items = MutableList(itemsSize) { itemNumber ->
                        StringListItem(
                                id = (listNumber * boardListsSize) + itemNumber,
                                value = "Item #${(listNumber * boardListsSize) + itemNumber}")
                    })
        })