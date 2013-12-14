#!/bin/bash
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.AutoGrader -x shCXR.coff
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_files.coff
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_files2.coff -# output=test_files2.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_matmult.coff -# output=test_matmult.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_proc.coff -# output=test_proc.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.UserGrader1  -x grader_user1.coff
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_files3.coff -# output=test_files3.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_illegal.coff -# output=test_illegal.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc.coff -# output=test_memalloc.out
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc2.coff
java -cp bin nachos.machine.Machine -[] nachos-sjtu/conf/proj2.conf -- nachos.ag.CoffGrader -x test_memalloc3.coff -# output=test_memalloc3.out
