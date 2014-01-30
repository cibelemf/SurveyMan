// generate json (maps from before)
// instead of setting the vars directly, just provide them as args to these functions
// and have the function set them

var SurveyMan = function (jsonSurvey) {

    var allQuestions        =   [],
        currentQuestions    =   [],
        topBlocks           =   [],
        questionsChosen     =   [],
        dropdownThreshold   =   7,
        id                  =   0,
        getQuestionById     =   function (quid) {

                                    var i;
                                    for ( i = 0 ; i < this.allQuestions.length ; i++ ) {
                                        if ( this.allQuestions[i].id === quid ) {
                                            return this.allQuestions[i];
                                        }
                                    }
                                    throw "Question id " + quid + " not found in allQuestions";

                                },
        range               =   function (n) {

                                    var i, rList = [];
                                    for ( i = 0 ; i < n ; i++ ) {
                                        rList.push(i);
                                    }
                                    return rList;

                                },
        getNextID           =   function() {
                                    id += 1;
                                    return "ans"+id;
                                },
        Block               =   function(jsonBlock) {

                                    var idStringToArray = function (_idString) {
                                        return _.map(_idString.split("."), function(s) { parseInt(s); });
                                    };

                                    this.id = jsonBlock.id;
                                    this.idArray = idStringToArray(jsonBlock.id);
                                    this.topLevelQuestions = Question.makeQuestions(jsonBlock.questions, this);
                                    this.subblocks = [];
                                    // may need to call a to boolean on jsonBlock.randomize
                                    this.randomizable = jsonBlock.randomize || Block.randomizeDefault;
                                    this.getAllBlockQuestions = function () {
                                        // either one question is a branch or all, and they're always out of the top level block.
                                        // put the current block's questions in a global stack that we can empty
                                        //  how to interleave top-level questions and blocks?
                                        //  get the total number of "slots" and assign indices
                                        var i, j = 0, k = 0,
                                            retval = [],
                                            indices = range(this.topLevelQuestions.length + this.subblocks.length),
                                            qindices = _.sample(indices, this.topLevelQuestions.length),
                                            bindices = _.difference(indices, qindices);
                                        for ( i = 0 ; i < indices.length ; i++ ) {
                                          // it happens that i == indices[i]
                                          if (_.contains(qindices, i)) {
                                            retval.push(this.topLevelQuestions[j]);
                                            j++;
                                          } else if (_.contains(bindices, i)) {
                                            retval.append(this.subblocks[k].getAllBlockQuestions);
                                            k++;
                                          } else throw "Neither qindices nor bindices contain index " + i;
                                        }
                                        return retval;
                                    };
                                    this.getQuestion = function(quid) {
                                        var i;
                                        for ( i = 0 ; i < this.topLevelQuestions.length ; i++ ) {
                                            if ( this.topLevelQuestions[i].id == quid ) {
                                                return this.topLevelQuestions[i];
                                            }
                                        }
                                        throw "Question with id " + quid + " not found in block " + this.id;
                                    };
                                    this.idComp = function(that) {
                                        // returns whether that follows (+1), precedes (-1), or is a sub-block (0) of this
                                        var i, j;
                                        for ( i = 0 ; i < this.idArray ; i++ ) {
                                            if ( i < that.idArray.length ) {
                                                if ( this.idArray[i] < that.idArray[i] ) {
                                                    return -1;
                                                } else if ( this.idArray[i] > that.idArray[i] ) {
                                                    return 1;
                                                }
                                            }
                                            return 0;
                                        }
                                    };
                                    this.randomize = function () {
                                        var i, j, newSBlocks = _.map(range(this.subblocks.length), function (foo) { return -1; });
                                        // randomize questions
                                        this.topLevelQuestions = _.shuffle(this.topLevelQuestions);
                                        if ( newSBlocks.length === 0 )
                                            return;
                                        // randomize blocks
                                        var stationaryBlocks = _.filter(this.subblocks, function (b) { return b.randomizable; }),
                                            nonStationaryBlocks = _.filter(this.subblocks, function (b) { return ! b.randomizable; }),
                                            samp = _.sample(range(this.subblocks.length), nonStationaryBlocks.length);
                                        _.shuffle(nonStationaryBlocks);
                                        for ( i = 0 ; i < samp.length ; i++ ) {
                                            // pick the locations for where to put the non-stationary blocks
                                            newSBlocks[samp[i]] = nonStationaryBlock[i];
                                        }
                                        for ( i = 0, j = 0; i < newSBlocks.length ; i++ ) {
                                            if ( newSBlocks[i] == -1 ) {
                                                newSBlocks[i] = stationaryBlocks[j];
                                                j++;
                                            }
                                        }
                                        console.assert(j == stationaryBlocks.length - 1);
                                        this.subblocks = newSBlocks;
                                        for ( i = 0 ; i < this.subblocks.length ; i++) {
                                            this.subblocks.randomize();
                                        }
                                    };
                                    this.populate = function () {
                                        var i;
                                        if (_.isUndefined(jsonBlock.subblocks)){
                                            console.log("No subblocks in Block " + this.id);
                                            return;
                                        }
                                        for ( i = 0 ; i < jsonBlock.subblocks.length ; i++ ) {
                                            var b = new Block(jsonBlock.subblocks[i]);
                                            b.parent = this;
                                            this.subblocks.push(b);
                                            b.populate();
                                        }
                                    };
                                    this.isLast = function (q) {
                                        return questions[questions.length - 1] === q;
                                    };
                                    // assert that the sub-blocks have the appropriate ids
                                    console.assert(_.every(this.subBlocks, function(b) { return this.idComp(b) === 0 }));

                                },
        Option              =   function(jsonOption, _question) {

                                    this.id = jsonOption.id;
                                    this.otext = jsonOption.otext;
                                    this.question = _question;

                                },
        Question            =   function(jsonQuestion, _block) {


                                    var makeBranchMap   =   function (branchMap, _question) {
                                                                var i, bm = {};
                                                                // branchMap -> map from oid to quid
                                                                if (!_.isUndefined(branchMap)) {
                                                                    var keys = _.keys(branchMap);
                                                                    for ( i = 0 ; i < keys.length ; i++ ) {
                                                                        var o = _question.getOption(keys[i]),
                                                                            q = getQuestionById(branchMap[keys[i]]);
                                                                            b = q.block;
                                                                        bm[o] = b;
                                                                    }
                                                                }
                                                                return bm;
                                                            };

                                    this.block = _block;
                                    this.id = jsonQuestion.id;
                                    this.qtext = jsonQuestion.qtext;
                                    this.freetext = jsonQuestion.freetext || Survey.freetextDefault;
                                    this.options = Option.makeOptions(jsonQuestion.options, this);
                                    this.branchMap = makeBranchMap(jsonQuestion.branchMap, this);
                                    // FIELDS MUST BE SENT OVER AS STRINGS
                                    this.randomizable = jsonQuestion.randomize || Survey.randomizeDefault;
                                    this.ordered = jsonQuestion.ordered || Survey.orderedDefault;
                                    this.exclusive = jsonQuestion.exclusive || Survey.exclusiveDefault;
                                    this.breakoff = jsonQuestion.breakoff || Survey.breakoffDefault;
                                    this.getOption = function (oid) {
                                        var i;
                                        for ( i = 0 ; i < options.length ; i++ ) {
                                            if ( options[i].id === oid ) {
                                                return options[i];
                                            }
                                        }
                                        throw "Option id " + oid + " not found in question " + this.id;
                                    };
                                    this.randomize = function () {
                                        var i;
                                        if (this.ordered) {
                                            if (Math.random() < 0.5) {
                                                options.reverse();
                                            }
                                        } else {
                                            _.shuffle(options);
                                        }
                                    };

                                },
        Survey              =   function (jsonSurvey) {

                                    var makeSurvey = function(jsonSurvey) {
                                        var i, blockList = [];
                                        for ( i = 0 ; i < jsonSurvey.length ; i++ ) {
                                            blockList[i] = new Block(jsonSurvey[i]);
                                            blockList[i].populate();
                                        }
                                        return blockList;
                                    };

                                    this.filename = jsonSurvey.filename;
                                    this.topLevelBlocks = makeSurvey(jsonSurvey.survey);
                                    this.breakoff = jsonSurvey.breakoff;
                                    this.randomize = function () {
                                        var i;
                                        for ( i = 0 ; i < this.topLevelBlocks.length ; i++ ) {
                                            // contents of the survey
                                            this.topLevelBlocks[i].randomize();
                                        }
                                        this.firstQuestion = this.topLevelBlocks[0].topLevelQuestions[0];
                                    };
                                };
    Survey.setFirstQuestion =   function(surveyInstance) {
                                    console.assert(surveyInstance.topLevelBlocks.length > 0);
                                        var firstBlock  =   surveyInstance.topLevelBlocks[0];
                                            firstQ      =   firstBlock.topLevelQuestions[0];
                                        surveyInstance.firstQuestion = firstQ;
                                };
    Question.makeQuestions  =   function (jsonQuestions, enclosingBlock) {
                                     var i, qList = [];
                                     for ( i = 0 ; i < jsonQuestions.length ; i++ ) {
                                         var q = new Question(jsonQuestions[i], enclosingBlock);
                                         qList.push(q);
                                         allQuestions.push(q);
                                     }
                                     return qList;
                                 };
    Option.makeOptions      =   function (jsonOptions, enclosingQuestion) {
                                     var i, oList = [];
                                     if (_.isUndefined(jsonOptions)) {
                                        var obj = _.keys(enclosingQuestion), str = "";
                                        for (i = 0 ; i < obj.length ; i++) {
                                            str += "\t" + obj[i] + ":" + enclosingQuestion[obj[i]] ;
                                        }
                                        console.log("No options defined for " + enclosingQuestion.id + " (" + str + ")");
                                        console.assert(enclosingQuestion.freetext);
                                        return;
                                     }
                                     for ( i = 0 ; i < jsonOptions.length ; i++ ){
                                         oList.push(new Option(jsonOptions[i], enclosingQuestion));
                                     }
                                     return oList;
                                 };
    Survey.exclusiveDefault =   true;
    Survey.orderedDefault   =   false;
    Survey.randomizeDefault =   true;
    Survey.freetextDefault  =   false;
    Survey.breakoffDefault  =   true;
    Block.randomizeDefault  =   false;

    var SM = {};
    SM.survey = new Survey(jsonSurvey);
    SM.showBreakoffNotice = function() {
        $(".question").append("<p> A button will appear momentarily to continue the survey. In the meantime, please read:</p><p>This survey will allow you to submit partial responses. The minimum payment is the quantity listed. However, you will be compensated more for completing more of the survey in the form of bonuses. The quantity paid depends on the results returned so far. Note that submitting partial results does not guarantee payment.</p>");
        $("div[name=question]").show();
        setTimeout(function () {
                        $(".question").append("<input type=\"button\" value=\"Continue\" onclick=\"sm.showFirstQuestion()\" />");
                        }
                    , 1000);
        };
    SM.showFirstQuestion = function() {
        var fileName = document.createElement("input");
        fileName.id = "filename";
        $(fileName).attr({hidden : true, value : SM.survey.filename, form : "mturk_form"});
        $("form").append(fileName);
        SM.showQuestion(SM.survey.firstQuestion);
        SM.showOptions(SM.survey.firstQuestion);
        currentQuestions = SM.survey.firstQuestion.block.getAllBlockQuestions();
        currentQuestions.shift();
        topBlocks.shift();
        console.log("getNextQuestion", currentQuestions.length);
    };
    SM.showQuestion =  function(q) {
        $(".question").empty();
        $(".question").append(q.qtext);
    };
    SM.showOptions = function(q) {
        $(".answer").empty();
        $(".answer").append(SM.getOptionHTML(q));
    };
    SM.showEarlySubmit = function (q) {
        return q.breakoff;
    };
    SM.getNextQuestion = function (q, o) {
        console.log("getNextQuestion", currentQuestions.length);
        var b;
        if (q.branchMap[o]) {
            // returns a block
            console.log("branching in question " + q.id);
            b = q.branchMap[o];
            currentQuestions = b.getAllBlockQuestions();
            return currentQuestions.shift();
        } else {
            // get the next sequential question
            if ( currentQuestions.length === 0 ) {
                // should never be called on empty topBlocks
                b = topBlocks.shift();
                currentQuestions = b.getAllBlockQuestions();
            }
            return currentQuestions.shift();
        }
    };
    SM.registerAnswerAndShowNextQuestion = function (pid, q, o) {
        $("form").append($("#"+pid));
        $("#"+pid).hide();
        questionsChosen.push(q);
        console.log(pid, q.id, o.id);
        $("#next_"+q.id).remove();
        $("#submit_"+q.id).remove();
        q = SM.getNextQuestion(q, o);
        SM.showQuestion(q);
        SM.showOptions(q);
    };
    SM.submitNotYetShown = function () {
        return $(":submit").length === 0;
    };
    SM.showNextButton = function (pid, q, o) {
        var id, nextHTML, submitHTML;
        id = "next_"+q.id;
        console.log(id, $("#"+id).length);
        if ($("#" + id).length > 0) return;
        if ( !(currentQuestions.length === 0 && topBlocks.length === 0) ) {
            nextHTML = document.createElement("input");
            nextHTML.id = id;
            nextHTML.type = "button";
            nextHTML.onclick = function () { sm.registerAnswerAndShowNextQuestion(pid, q, o); };
            nextHTML.value = "Next";
            $("div[name=question]").append(nextHTML);
        }
        if (SM.submitNotYetShown()) {
            submitHTML = document.createElement("input");
            submitHTML.type = "submit";
            submitHTML.id = "submit_" + q.id;
            if ( currentQuestions.length === 0 && topBlocks.length === 0)
                $(submitHTML).attr({value : "Submit"});
            else if (SM.showEarlySubmit(q.id, o.id))
                $(submitHTML).attr({value : "Submit Early", class : "breakoff"});
            $("div[name=question]").append(submitHTML);
        }
    };
    SM.getDropdownOpt = function(q) {
        var dropdownOpt = $("#select_" + q.id + " option:selected");
        console.log("selected dropdown option: " + dropdownOpt);
        return dropdownOpt;
    };
    SM.getOptionHTML = function (q) {
        // would like to replace text area, select, etc. with JS objects
        var o, i, elt, dummy, retval,
            pid     =   getNextID(),
            par     =   document.createElement("p");
        par.id = pid;
        if ( q.freetext ) {
            elt = document.createElement("textarea");
            elt.id = q.id;
            elt.type = "text";
            elt.oninput = function () { sm.showNextButton(pid, q, -1); };
            $(elt).attr({ name : q.id, form :  "mturk_form" });
            $(par).append(elt);
        } else if ( q.options.length > dropdownThreshold ) {
            elt = document.createElement("select");
            elt.id = "select_" + q.id;
            elt.onchange = function () { sm.showNextButton(pid, q, sm.getDropdownOpt(q)); };
            $(elt).attr({ name : q.id, form : "mturk_form" });
            if (!q.exclusive) {
                $(elt).prop("multiple", true);
            }
            dummy = new Option("CHOOSE ONE");
            $(dummy).attr({disable : true, selected : true});
            $(elt).append(dummy);
            for ( i = 0 ; i < q.options.length ; i++ ) {
                retval = {"quid" : q.id, "oid" : o.id, "qpos" : questionsChosen.length, "opos" : i};
                o = new Option(o.text,  JSON.stringify(retval))
                o.id = o.oid;
                $(elt).append(o);
            }
            $(par).append(elt);
        } else {
            for ( i = 0 ; i < q.options.length ; i++) {
                var opt = q.options[i];
                retval = {"quid" : q.id, "oid" : opt.id, "qpos" : questionsChosen.length, "opos" : i};
                elt = document.createElement("label");
                $(elt).attr("for", opt.oid);
                o = document.createElement("input");
                o.type = q.exclusive ? "radio" : "check";
                o.id = q.id;
                o.onchange = function () { sm.showNextButton(pid, q, opt) };
                $(o).attr({ name : q.id
                            , value : JSON.stringify(retval)
                            , name : q.id
                            , form : "mturk_form"
                            });
                console.log(o);
                $(elt).append(o);
                $(elt).append(opt.otext);
                $(par).append(elt);
            }
        }
        return par;
    };

    Survey.setFirstQuestion(SM.survey);
    topBlocks = SM.survey.topLevelBlocks;
    SM.survey.randomize();

    return SM;

};

/*
{b1 : {id : "1.1.1"
        randomize : False
       questions: //top level questions as usual
       subblocks : {b1.1 : { ... }
       ...
       }


*/
