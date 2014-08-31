#!/usr/bin/python

try :
  from peewee import Model, SqliteDatabase, CharField, TextField
except ImportError :
  print "Install peewee by 'sudo pip install peewee'"
  exit(0)

import os


DB_NAME = os.path.join(os.path.dirname(__file__), 'oui.db')
OUI_SOURCE = os.path.join(os.path.dirname(__file__), 'oui.txt')
OUI_LEN = 8

try :
  os.remove(DB_NAME)
except :
  pass


db = SqliteDatabase(DB_NAME, threadlocals=True)

class OUI(Model) :
  class Meta :
    database = db

  oui = CharField(unique=True, max_length=OUI_LEN)
  short_name = TextField()
  long_name = TextField()

db.connect()
db.create_tables([OUI,])

with open(OUI_SOURCE, 'r') as f :
  for line in f.readlines() :
    if line.startswith('#') :
      continue
    parts = line.split()
    if len(parts) <= 1 :
      continue
    if len(parts[0]) != OUI_LEN :
      continue
    oui = parts[0]
    short_name = parts[1]

    parts = line.split('#')
    if len(parts) == 2 :
      long_name = parts[1].strip()
    else :
      long_name = short_name
    OUI.create(oui=oui, short_name=short_name, long_name=long_name)
