package uk.whitecrescent.example

data class ListItem<T>(
        var id: Int,
        var value: T
)

typealias StringListItem = ListItem<String>

data class BoardList(
        var id: Int,
        var name: String,
        val items: List<StringListItem>
) {
    operator fun get(index: Int) = items[index]
}

data class Board<T>(
        var id: Int,
        var name: String,
        val boardLists: List<BoardList>
) {
    operator fun get(index: Int) = boardLists[index]
}

val boardListsSize = 100
val itemsSize = 100

val exampleBoard = Board<String>(
        id = 69420,
        name = "My Board",
        boardLists = List(boardListsSize) { listNumber ->
            BoardList(
                    id = listNumber,
                    name = "List #$listNumber",
                    items = List(itemsSize) { itemNumber ->
                        StringListItem(
                                id = (listNumber * boardListsSize) + itemNumber,
                                value = "Item #${(listNumber * boardListsSize) + itemNumber}")
                    })
        })