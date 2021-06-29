pragma solidity ^0.4.24;

/// @title Testing transfer calls from contract
contract Transfer {

    function transfer(address destination, uint value)
    public
    {
        destination.transfer(value);
    }

    function send(address destination, uint value)
    public
    returns (bool)
    {
        return destination.send(value);
    }
}
