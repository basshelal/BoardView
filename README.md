# BoardView (Work in Progress)

Easy to use Android library that provides a `View` for displaying 
[Kanban Boards](https://en.wikipedia.org/wiki/Kanban_board) 
similar to those found in Trello, Asana, Jira, KanbanFlow etc.

## Limitations

* Board Columns must be of the same width, so `WRAP_CONTENT` is not supported due to its
   unpredictable dynamic nature.
* The gestures that drag columns and items are unfortunately **not yet Accessibility friendly**, 
  this is something that needs to be explored in more detail to be solved properly.
  
# Design Choices

This library is meant to be, for the most part, and out of the box, high level solution.
It provides you with the ability to easily customize many aspects of the View while keeping things
high level and abstract enough. As a result, it may not provide the low level detailed tweaking that
some applications may require.