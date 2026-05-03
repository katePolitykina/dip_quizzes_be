# Program Schema

High-level ISO 5807-oriented schema in PlantUML.

Constraints applied:
- high abstraction level
- maximum 20 blocks
- maximum 5 words per block

PlantUML mapping:
- `start` / `stop` -> terminal
- `:...;` -> process or input/output
- `if / else / endif` -> decision

```plantuml
@startuml
title Program Schema

start

:Open application;

if (Session exists?) then (yes)
  :Restore context;
else (no)
  if (Host access?) then (yes)
    :Authenticate host;
  else (no)
    :Authenticate guest;
  endif
endif

if (Management flow?) then (yes)
  :Manage profile;
  :Manage quizzes;
  :Create or join room;
else (no)
  :Open room;
endif

:Sync room state;

if (Host controls?) then (yes)
  :Configure lobby;
  :Start game;
else (no)
  :Await room updates;
endif

if (Game active?) then (yes)
  :Run question cycle;
  :Process team actions;
  :Score and rank;
else (no)
endif

if (Game finished?) then (yes)
  :Show final results;
  if (Export results?) then (yes)
    :Export detailed report;
  else (no)
  endif
else (no)
endif

:Leave or continue;

stop
@enduml
```
