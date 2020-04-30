const mongoose = require('mongoose');
const Schema = mongoose.Schema;

const RobotSchema = new Schema({
    name: {
        type: String,
        required: true,
    },
    serialNumber: {
        type: String,
        required: true,
    },
    date: {
        type: Date,
        default: Date.now
    }
});

const UserSchema = new Schema({
    date: {
    	type: Date,
    	default: Date.now
    },
    userName: {
    	type: String,
    	required: false
    },
    lastName: {
    	type: String,
    	required: true
    },
    firstName: {
    	type: String,
    	required: true
    },
    email: {
    	type: String,
    	required: true
    },
    githubId: {
    	type: String,
    	required: false
    },
    azureId: {
    	type: String,
    	required: false
    },
    robots: [RobotSchema]
});

const User = mongoose.model('user', UserSchema);

module.exports = User;