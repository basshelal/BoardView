# BoardView (Work in Progress)

Android Library that provides a `View` for displaying Kanban Boards similar to those found
 in Trello, Asana, Jira, KanbanFlow etc.

## Known Issues

* Board Columns must be of the same width, so `WRAP_CONTENT` is not supported due to its
   unpredictable dynamic nature.
* The gestures that drag columns and items are unfortunately **not Accessibility friendly**, 
  this is something that needs to be explored in more detail to be solved properly.